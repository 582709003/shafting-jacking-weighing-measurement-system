package com.shafting.weighing.device.service;

import com.shafting.weighing.alarm.service.AlarmService;
import com.shafting.weighing.control.service.ControlStateService;
import com.shafting.weighing.device.config.PlcProperties;
import com.shafting.weighing.device.dto.RealtimeSnapshot;
import com.shafting.weighing.device.entity.MeasurementSample;
import com.shafting.weighing.device.mapper.MeasurementSampleMapper;
import com.shafting.weighing.task.entity.TestRun;
import com.shafting.weighing.task.mapper.TestRunMapper;
import com.shafting.weighing.task.service.MeasurementTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLC 模式下的状态、传感器和稳定采样轮询服务。
 *
 * <p>S7-1200 不使用订阅。本服务每个周期批量读取反馈及六通道实时值；只有 PLC 采样序号递增时
 * 才保存自动测试采样，并且先保存最后一个采样，再处理完成状态。</p>
 */
@Component
public class PlcPollingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlcPollingService.class);
    private static final String COMMUNICATION_ALARM = "PLC_COMMUNICATION";
    private static final String PROCESSING_ALARM = "PLC_DATA_PROCESSING";

    private final PlcProperties properties;
    private final DeviceCommandGateway deviceGateway;
    private final ControlStateService stateService;
    private final MeasurementSampleMapper sampleMapper;
    private final TestRunMapper runMapper;
    private final MeasurementTaskService taskService;
    private final RealtimeStateStore realtimeStateStore;
    private final RealtimePublisher realtimePublisher;
    private final AlarmService alarmService;
    private final Map<Long, Integer> lastStageByRun = new ConcurrentHashMap<>();

    public PlcPollingService(PlcProperties properties,
                             DeviceCommandGateway deviceGateway,
                             ControlStateService stateService,
                             MeasurementSampleMapper sampleMapper,
                             TestRunMapper runMapper,
                             MeasurementTaskService taskService,
                             RealtimeStateStore realtimeStateStore,
                             RealtimePublisher realtimePublisher,
                             AlarmService alarmService) {
        this.properties = properties;
        this.deviceGateway = deviceGateway;
        this.stateService = stateService;
        this.sampleMapper = sampleMapper;
        this.runMapper = runMapper;
        this.taskService = taskService;
        this.realtimeStateStore = realtimeStateStore;
        this.realtimePublisher = realtimePublisher;
        this.alarmService = alarmService;
    }

    /** 按配置周期读取 PLC；模拟模式立即返回，不建立现场连接。 */
    @Scheduled(fixedDelayString = "${app.plc.poll-interval-ms:200}")
    public void poll() {
        if (!properties.isPlcMode()) {
            return;
        }
        Map<String, BigDecimal> values;
        try {
            values = deviceGateway.readFeedback(allPollingAddresses());
            alarmService.updatePlcSignal(COMMUNICATION_ALARM, false, "S7-1200", "PLC 通讯已恢复");
        } catch (RuntimeException exception) {
            LOGGER.warn("PLC 轮询失败：{}", exception.getMessage());
            try {
                alarmService.updatePlcSignal(COMMUNICATION_ALARM, true, "S7-1200",
                        "PLC 通讯失败，请检查网络、连接参数和点位配置");
            } catch (RuntimeException alarmException) {
                LOGGER.error("记录 PLC 通讯报警失败", alarmException);
            }
            return;
        }
        try {
            handleValues(values);
            alarmService.updatePlcSignal(PROCESSING_ALARM, false, "Java 数据处理", "PLC 数据处理已恢复");
        } catch (RuntimeException exception) {
            LOGGER.error("PLC 反馈处理失败", exception);
            try {
                alarmService.updatePlcSignal(PROCESSING_ALARM, true, "Java 数据处理",
                        "PLC 反馈已读取，但状态同步或采样保存失败");
            } catch (RuntimeException alarmException) {
                LOGGER.error("记录 PLC 数据处理报警失败", alarmException);
            }
        }
    }

    private void handleValues(Map<String, BigDecimal> values) {
        int selectedChannel = intValue(values, "selectedChannel");
        int mode = intValue(values, "mode");
        int action = intValue(values, "action");
        int runState = intValue(values, "runState");
        int processStage = intValue(values, "processStage");
        long sequence = longValue(values, "sampleSequence");
        int faultCode = intValue(values, "faultCode");
        String selectedPoint = PlcPointCatalog.pointCode(selectedChannel);

        // 完成或故障反馈可能同时把 PLC 模式复位为断开，需先保留 Java 已关联的运行。
        Long previousRunId = stateService.activeRunId();
        String previousPointCode = stateService.activePointCode();
        stateService.synchronizePlcFeedback(selectedPoint, mode, action, runState);
        Long activeRunId = previousRunId == null ? stateService.activeRunId() : previousRunId;
        String activePointCode = previousPointCode == null ? stateService.activePointCode() : previousPointCode;
        TestRun activeRun = activeRunId == null ? null : runMapper.selectById(activeRunId);
        Instant capturedAt = Instant.now();

        synchronizeAlarmSignals(values);

        Map<String, SensorReading> readings = publishRealtimeValues(values, activeRun, activePointCode,
                runState, processStage, capturedAt);
        if (activeRun != null && sequence > 0) {
            SensorReading activeReading = readings.get(activePointCode);
            persistSampleIfNew(activeRun, sequence, processStage, activeReading);
            lastStageByRun.put(activeRunId, processStage);
        }

        // 采样必须先于结束状态处理，保证完成反馈同周期携带的最终点不会丢失。
        if (activeRunId != null && runState == 3) {
            taskService.completeFromPlc(activeRunId);
            stateService.finishAction(activePointCode, activeRunId);
            lastStageByRun.remove(activeRunId);
        } else if (activeRunId != null && runState == 4) {
            String code = "PLC_FAULT_" + faultCode;
            taskService.stopFromPlc(activeRunId, code);
            alarmService.activateFromPlc(code, activePointCode, "PLC 自动测试反馈故障，故障码：" + faultCode);
            lastStageByRun.remove(activeRunId);
        }
    }

    private Map<String, SensorReading> publishRealtimeValues(Map<String, BigDecimal> values,
                                                              TestRun activeRun,
                                                              String activePointCode,
                                                              int runState,
                                                              int processStage,
                                                              Instant capturedAt) {
        Map<String, SensorReading> readings = new LinkedHashMap<>();
        for (String pointCode : PlcPointCatalog.pointCodes()) {
            SensorReading reading = new SensorReading(decimal(values, pointCode + ".displacement"),
                    decimal(values, pointCode + ".force"),
                    intValue(values, pointCode + ".motorRpm"), capturedAt);
            readings.put(pointCode, reading);
            boolean active = activeRun != null && pointCode.equals(activePointCode);
            RealtimeSnapshot snapshot = new RealtimeSnapshot(
                    active ? activeRun.getId() : null,
                    active ? activeRun.getTaskId() : null,
                    pointCode,
                    active ? runStateName(runState) : "IDLE",
                    active ? processStageName(processStage) : "WAITING",
                    reading.displacementMm().setScale(2, RoundingMode.HALF_UP),
                    reading.motorRpm(),
                    reading.forceKn().setScale(1, RoundingMode.HALF_UP),
                    capturedAt);
            if (active) {
                realtimeStateStore.save(snapshot);
            }
            realtimePublisher.publish(snapshot);
        }
        return readings;
    }

    private void persistSampleIfNew(TestRun run, long sequence, int processStage, SensorReading reading) {
        if (reading == null || sampleMapper.countByRunAndSequence(run.getId(), sequence) > 0) {
            return;
        }
        MeasurementSample sample = new MeasurementSample();
        sample.setRunId(run.getId());
        sample.setPointId(run.getPointId());
        sample.setStage(sampleStage(run.getId(), processStage));
        sample.setSequenceNo(sequence);
        sample.setCapturedAt(reading.capturedAt());
        sample.setForceKn(reading.forceKn().setScale(1, RoundingMode.HALF_UP));
        sample.setDisplacementMm(reading.displacementMm().setScale(2, RoundingMode.HALF_UP));
        sample.setRawPayloadJson("{\"source\":\"PLC\"}");
        sample.setValid(1);
        sampleMapper.insert(sample);
    }

    private String sampleStage(Long runId, int stage) {
        int effectiveStage = stage == 5 ? lastStageByRun.getOrDefault(runId, 3) : stage;
        return effectiveStage == 1 || effectiveStage == 2 ? "LIFTING" : "LOWERING";
    }

    private Map<String, String> allPollingAddresses() {
        Map<String, String> addresses = new LinkedHashMap<>();
        addresses.put("selectedChannel", properties.getFeedbackSelectedChannel());
        addresses.put("mode", properties.getFeedbackMode());
        addresses.put("action", properties.getFeedbackAction());
        addresses.put("runState", properties.getFeedbackRunState());
        addresses.put("processStage", properties.getFeedbackProcessStage());
        addresses.put("sampleSequence", properties.getFeedbackSampleSequence());
        addresses.put("faultCode", properties.getFeedbackFaultCode());
        for (String pointCode : PlcPointCatalog.pointCodes()) {
            PlcPointCatalog.ChannelTags tags = PlcPointCatalog.channel(pointCode);
            addresses.put(pointCode + ".displacement", tags.displacement());
            addresses.put(pointCode + ".force", tags.force());
            addresses.put(pointCode + ".motorRpm", tags.motorRpm());
        }
        for (PlcAlarmCatalog.AlarmTag tag : PlcAlarmCatalog.tags()) {
            addresses.put("alarm." + tag.code(), tag.address());
        }
        return addresses;
    }

    private void synchronizeAlarmSignals(Map<String, BigDecimal> values) {
        for (PlcAlarmCatalog.AlarmTag tag : PlcAlarmCatalog.tags()) {
            alarmService.updatePlcSignal(tag.code(), intValue(values, "alarm." + tag.code()) != 0,
                    "S7-1200 DB12", tag.message());
        }
    }

    private BigDecimal decimal(Map<String, BigDecimal> values, String key) {
        BigDecimal value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("PLC 批量读取缺少字段：" + key);
        }
        return value;
    }

    private int intValue(Map<String, BigDecimal> values, String key) {
        return decimal(values, key).intValue();
    }

    private long longValue(Map<String, BigDecimal> values, String key) {
        return decimal(values, key).longValue();
    }

    private String runStateName(int state) {
        return switch (state) {
            case 1 -> "RUNNING";
            case 2 -> "PAUSED";
            case 3 -> "COMPLETED";
            case 4 -> "STOPPED";
            default -> "IDLE";
        };
    }

    private String processStageName(int stage) {
        return switch (stage) {
            case 1 -> "LIFTING";
            case 2 -> "LIFTING_HOLDING";
            case 3 -> "LOWERING";
            case 4 -> "LOWERING_HOLDING";
            case 5 -> "COMPLETED";
            default -> "WAITING";
        };
    }
}
