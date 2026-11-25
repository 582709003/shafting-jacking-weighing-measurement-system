package com.shafting.weighing.device.service;

import com.shafting.weighing.control.dto.ControlDto;
import com.shafting.weighing.control.service.ControlStateService;
import com.shafting.weighing.device.config.SimulatorProperties;
import com.shafting.weighing.device.dto.RealtimeSnapshot;
import com.shafting.weighing.device.dto.SimulationStart;
import com.shafting.weighing.device.entity.MeasurementSample;
import com.shafting.weighing.device.mapper.MeasurementSampleMapper;
import com.shafting.weighing.task.entity.MeasurementTask;
import com.shafting.weighing.task.entity.TestRun;
import com.shafting.weighing.task.mapper.MeasurementTaskMapper;
import com.shafting.weighing.task.mapper.TestRunMapper;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
/**
 * 无现场 PLC 时使用的单机设备模拟器。
 *
 * <p>它按配置时间推进自动流程，生成实时快照、保存原始采样，并在完成时更新任务状态。
 * 这里的数值和流程仅用于软件联调，不能替代真实设备控制和安全联锁。</p>
 */
public class DeviceSimulator {

    private final Map<Long, AutomaticProcessSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ManualSession> manualSessions = new ConcurrentHashMap<>();
    private final Map<String, ManualSensorState> manualSensorStates = new ConcurrentHashMap<>();
    private final Set<String> automaticQuickReleasePoints = ConcurrentHashMap.newKeySet();
    private final RealtimeStateStore realtimeStateStore;
    private final RealtimePublisher realtimePublisher;
    private final MeasurementSampleMapper sampleMapper;
    private final TestRunMapper testRunMapper;
    private final MeasurementTaskMapper taskMapper;
    private final SimulatorProperties properties;
    private final ControlStateService controlStateService;
    private final DeviceCommandGateway deviceCommandGateway;

    public DeviceSimulator(RealtimeStateStore realtimeStateStore,
                           RealtimePublisher realtimePublisher,
                           MeasurementSampleMapper sampleMapper,
                           TestRunMapper testRunMapper,
                           MeasurementTaskMapper taskMapper,
                           SimulatorProperties properties,
                           ControlStateService controlStateService,
                           DeviceCommandGateway deviceCommandGateway) {
        this.realtimeStateStore = realtimeStateStore;
        this.realtimePublisher = realtimePublisher;
        this.sampleMapper = sampleMapper;
        this.testRunMapper = testRunMapper;
        this.taskMapper = taskMapper;
        this.properties = properties;
        this.controlStateService = controlStateService;
        this.deviceCommandGateway = deviceCommandGateway;
    }

    /**
     * 创建运行内存会话，并将未配置或为 0 的参数替换为模拟器默认值。
     *
     * @param start 本次运行和测量点参数
     */
    public void start(SimulationStart start) {
        BigDecimal displacement = positiveOr(start.maxDisplacementMm(), new BigDecimal("0.40"));
        BigDecimal force = positiveOr(start.standardForceKn(), properties.getFallbackForceKn());
        int rpm = start.motorRpm() != null && start.motorRpm() > 0 ? start.motorRpm() : 1_200;
        BigDecimal pressureIntervalMpa = positiveOr(start.pressureIntervalMpa(), BigDecimal.ZERO);
        long intervalHoldMillis = Math.max(0L, start.holdSeconds() == null
                ? 0L : start.holdSeconds() * 1_000L);
        SimulationLimits limits = new SimulationLimits(displacement, rpm, force,
                pressureIntervalMpa, intervalHoldMillis);
        sessions.put(start.runId(), new AutomaticProcessSession(start.taskId(), start.pointId(),
                start.pointCode(), limits, properties));
    }

    /** 暂停自动测试会话。 */
    public void pause(Long runId) {
        AutomaticProcessSession session = requireSession(runId);
        synchronized (session) {
            session.pause(System.currentTimeMillis());
            deviceCommandGateway.sendPause(session.pointCode());
        }
    }

    /** 恢复自动测试会话，并重置计时间隔起点。 */
    public void resume(Long runId) {
        AutomaticProcessSession session = requireSession(runId);
        synchronized (session) {
            dispatch(session.pointCode(), session.resume(System.currentTimeMillis()));
        }
    }

    /** 删除内存会话并向工作页推送最后一条 STOPPED 快照。 */
    public void stop(Long runId) {
        AutomaticProcessSession session = sessions.remove(runId);
        if (session != null && properties.isEnabled()) {
            deviceCommandGateway.sendPause(session.pointCode());
            publishAutomatic(session.snapshot(runId, "STOPPED"));
        }
    }

    /**
     * 启动手动设备动作。
     *
     * <p>手动操作不创建任务、不保存采样。存在真实设备读数时优先使用真实值；设备网关
     * 暂未返回读数时，可使用手动模拟值联调页面，并按位移判断是否达到已保存目标。</p>
     */
    public void startManualAction(String pointCode, ControlDto.ChannelAction action,
                                  BigDecimal targetDisplacementMm) {
        ManualSensorState sensor = manualSensorStates.computeIfAbsent(pointCode,
                ignored -> new ManualSensorState());
        ManualSession session = new ManualSession(pointCode, action, targetDisplacementMm,
                sensor.displacementMm, sensor.forceKn, System.currentTimeMillis());
        manualSessions.put(pointCode, session);
        dispatchManual(session);
    }

    /**
     * 将指定通道的位移传感器清零，并立即向工作页发布 0.00 mm 实时值。
     *
     * <p>当前设备网关只保留 PLC 写零调用位置；模拟状态仅修改位移，称重传感器
     * 当前力值保持不变，避免“位移清零”误清除其他传感器数据。</p>
     *
     * @param pointCode 测量点稳定编码
     */
    public void zeroDisplacement(String pointCode) {
        deviceCommandGateway.sendDisplacementZero(pointCode);
        ManualSensorState sensor = manualSensorStates.computeIfAbsent(pointCode,
                ignored -> new ManualSensorState());
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        sensor.displacementMm = zero;
        realtimePublisher.publish(new RealtimeSnapshot(null, null, pointCode, "IDLE", "PRECHECK",
                zero, 0, sensor.forceKn, Instant.now()));
    }

    /** 启动六通道自动快泄模拟；复用通道实时流，不创建任务、运行或采样记录。 */
    public void startAutoQuickRelease(List<String> pointCodes) {
        pointCodes.forEach(pointCode -> {
            automaticQuickReleasePoints.add(pointCode);
            startManualAction(pointCode, ControlDto.ChannelAction.MANUAL_QUICK_RELEASE, null);
        });
    }

    /** 暂停仍在快泄的通道，并保留各通道当前模拟值。 */
    public void pauseAutoQuickRelease() {
        automaticQuickReleasePoints.forEach(pointCode -> {
            ManualSession session = manualSessions.get(pointCode);
            if (session != null) {
                synchronized (session) {
                    session.pause(System.currentTimeMillis());
                    deviceCommandGateway.sendPause(pointCode);
                }
            }
        });
    }

    /** 从暂停位置恢复六通道自动快泄。 */
    public void resumeAutoQuickRelease() {
        automaticQuickReleasePoints.forEach(pointCode -> {
            ManualSession session = manualSessions.get(pointCode);
            if (session != null) {
                synchronized (session) {
                    session.resume(System.currentTimeMillis());
                    dispatchManual(session);
                }
            }
        });
    }

    /** 停止全部自动快泄会话，保留停止时的模拟传感器值。 */
    public void stopAutoQuickRelease() {
        List.copyOf(automaticQuickReleasePoints).forEach(pointCode -> {
            ManualSession session = manualSessions.remove(pointCode);
            if (session != null) {
                deviceCommandGateway.sendStop(pointCode);
            }
            automaticQuickReleasePoints.remove(pointCode);
        });
    }

    /** 暂停当前通道的手动动作，并保留原动作供继续操作。 */
    public void pauseManual(String pointCode) {
        ManualSession session = requireManualSession(pointCode);
        synchronized (session) {
            session.pause(System.currentTimeMillis());
            deviceCommandGateway.sendPause(pointCode);
        }
    }

    /** 恢复暂停前的手动动作。 */
    public void resumeManual(String pointCode) {
        ManualSession session = requireManualSession(pointCode);
        synchronized (session) {
            session.resume(System.currentTimeMillis());
            dispatchManual(session);
        }
    }

    /** 停止并清除当前通道的手动动作。 */
    public void stopManual(String pointCode) {
        ManualSession session = manualSessions.remove(pointCode);
        if (session != null) {
            deviceCommandGateway.sendStop(pointCode);
        }
    }

    /** 报警联锁时停止全部无运行记录的设备动作，包括手动动作和自动快泄。 */
    public void stopAllManual() {
        manualSessions.keySet().forEach(deviceCommandGateway::sendStop);
        manualSessions.clear();
        automaticQuickReleasePoints.clear();
    }

    /**
     * 定时推进全部活动会话。
     *
     * <p>实时值每个周期推送；只有力值阈值暂停结束、最高位移暂停结束及下降归零时保存采样。</p>
     */
    @Scheduled(fixedRateString = "${app.simulator.interval-ms:500}")
    public void tick() {
        if (properties.isEnabled()) {
            // sessions 保存当前正在运行的自动测试会话。
            // 虽然代码支持遍历多个会话，但上层控制逻辑目前限制全系统同一时刻只能有一个活动通道。
            sessions.forEach((runId, session) -> {
                synchronized (session) {
                    SensorReading reading = deviceCommandGateway.readSensorValues(session.pointCode());
                    AutomaticStep step = session.advance(System.currentTimeMillis(), reading);
                    dispatch(session.pointCode(), step.deviceAction());
                    RealtimeSnapshot snapshot = session.snapshot(runId, session.state());
                    rememberSensorState(session.pointCode(), snapshot.displacementMm(), snapshot.forceKn());
                    publishAutomatic(snapshot);
                    updateProcessStage(runId, snapshot.processStage());
                    if (step.sampleStage() != null) {
                        persistAutomaticSample(session, snapshot, step.sampleStage(), reading != null);
                    }
                    if (step.completed()) {
                        complete(runId, session.taskId());
                        sessions.remove(runId, session);
                        controlStateService.finishAction(session.pointCode(), runId);
                    }
                }
            });
        }
        // 手动动作始终优先读取真实设备；网关暂无数据时才按配置启用页面联调模拟值。
        manualSessions.forEach(this::tickManual);
    }

    /** 读取手动动作实时值；只有上升达到保存目标时由后台自动停止。 */
    private void tickManual(String pointCode, ManualSession session) {
        synchronized (session) {
            SensorReading reading = deviceCommandGateway.readSensorValues(pointCode);
            if (reading == null && properties.isManualEnabled()) {
                reading = simulateManualReading(session, System.currentTimeMillis());
            }
            if (reading == null) {
                return;
            }
            rememberSensorState(pointCode, reading.displacementMm(), reading.forceKn());
            boolean targetReached = !session.paused
                    && session.action == ControlDto.ChannelAction.ASCENDING
                    && reading.displacementMm().compareTo(session.targetDisplacementMm) >= 0;
            boolean returnedToZero = !session.paused
                    && session.action != ControlDto.ChannelAction.ASCENDING
                    && reading.forceKn().signum() <= 0;
            if (targetReached || returnedToZero) {
                // 先停止设备，再更新页面状态，避免界面显示停止但设备仍继续顶升。
                deviceCommandGateway.sendStop(pointCode);
                manualSessions.remove(pointCode, session);
                realtimePublisher.publish(manualSnapshot(session, reading, "STOPPED"));
                if (!automaticQuickReleasePoints.remove(pointCode)) {
                    controlStateService.finishAction(pointCode, null);
                }
                return;
            }
            realtimePublisher.publish(manualSnapshot(session, reading, session.paused ? "PAUSED" : "RUNNING"));
        }
    }

    /**
     * 按动作实际经过时间生成手动联调读数，不依赖调度周期次数。
     * 上升使用 lifting-ms，下降使用 lowering-ms，快泄使用三分之一下降时长。
     */
    private SensorReading simulateManualReading(ManualSession session, long nowMillis) {
        long durationMillis = session.action == ControlDto.ChannelAction.ASCENDING
                ? properties.getLiftingMs()
                : session.action == ControlDto.ChannelAction.MANUAL_QUICK_RELEASE
                ? Math.max(1L, properties.getLoweringMs() / 3L)
                : properties.getLoweringMs();
        double ratio = Math.min(1D, session.activeElapsed(nowMillis) / (double) Math.max(1L, durationMillis));
        BigDecimal progress = BigDecimal.valueOf(ratio);
        BigDecimal displacement;
        BigDecimal force;
        if (session.action == ControlDto.ChannelAction.ASCENDING) {
            BigDecimal target = session.targetDisplacementMm.max(session.initialDisplacementMm);
            displacement = interpolate(session.initialDisplacementMm, target, progress);
            BigDecimal peakForce = properties.getFallbackForceKn().max(session.initialForceKn);
            force = interpolate(session.initialForceKn, peakForce, progress);
        } else {
            BigDecimal remaining = BigDecimal.ONE.subtract(progress);
            displacement = session.initialDisplacementMm.multiply(remaining);
            force = session.initialForceKn.multiply(remaining);
        }
        return new SensorReading(displacement.setScale(2, RoundingMode.HALF_UP),
                force.setScale(1, RoundingMode.HALF_UP),
                session.paused ? 0 : properties.getManualMotorRpm(), Instant.ofEpochMilli(nowMillis));
    }

    private BigDecimal interpolate(BigDecimal start, BigDecimal end, BigDecimal progress) {
        return start.add(end.subtract(start).multiply(progress));
    }

    private void rememberSensorState(String pointCode, BigDecimal displacementMm, BigDecimal forceKn) {
        ManualSensorState sensor = manualSensorStates.computeIfAbsent(pointCode,
                ignored -> new ManualSensorState());
        sensor.displacementMm = displacementMm;
        sensor.forceKn = forceKn;
    }

    private RealtimeSnapshot manualSnapshot(ManualSession session, SensorReading reading, String state) {
        return new RealtimeSnapshot(null, null, session.pointCode, state,
                manualStage(session.action), reading.displacementMm(),
                reading.motorRpm(), reading.forceKn(), reading.capturedAt());
    }

    /** 手动实时快照使用现有阶段名称，以复用工作页流程显示。 */
    private String manualStage(ControlDto.ChannelAction action) {
        return action == ControlDto.ChannelAction.ASCENDING ? "LIFTING" : "LOWERING";
    }

    /** 自动流程同一快照先写 Redis 最新状态，再广播给当前 SSE 订阅者。 */
    private RealtimeSnapshot publishAutomatic(RealtimeSnapshot snapshot) {
        realtimeStateStore.save(snapshot);
        realtimePublisher.publish(snapshot);
        return snapshot;
    }

    /** 仅将暂停稳定点或下降归零点保存为自动测试原始采样。 */
    private void persistAutomaticSample(AutomaticProcessSession session, RealtimeSnapshot snapshot,
                                        String sampleStage, boolean deviceReading) {
        MeasurementSample sample = new MeasurementSample();
        sample.setRunId(snapshot.runId());
        sample.setPointId(session.pointId());
        sample.setStage(sampleStage);
        sample.setSequenceNo(session.nextSequence());
        sample.setCapturedAt(snapshot.capturedAt());
        sample.setForceKn(snapshot.forceKn());
        sample.setDisplacementMm(snapshot.displacementMm());
        sample.setRawPayloadJson(deviceReading
                ? "{\"source\":\"DEVICE\",\"sampling\":\"INTERVAL_HOLD\"}"
                : "{\"source\":\"SIMULATOR\",\"sampling\":\"INTERVAL_HOLD\"}");
        sample.setValid(1);
        sampleMapper.insert(sample);
    }

    /** 将状态机动作映射到预留设备方法，现场协议确认后只需实现设备网关。 */
    private void dispatch(String pointCode, DeviceAction action) {
        switch (action) {
            case ASCEND -> deviceCommandGateway.sendAscending(pointCode);
            case PAUSE -> deviceCommandGateway.sendPause(pointCode);
            case DESCEND -> deviceCommandGateway.sendDescending(pointCode);
            case STOP -> deviceCommandGateway.sendStop(pointCode);
            case NONE -> {
                // 本周期仅更新状态或等待保持时间，无需下发指令。
            }
        }
    }

    /** 流程阶段变化时才更新数据库，避免每 500 ms 重复写相同状态。 */
    private void updateProcessStage(Long runId, String stage) {
        TestRun run = testRunMapper.selectById(runId);
        if (run != null && !stage.equals(run.getProcessStage())) {
            run.setProcessStage(stage);
            testRunMapper.updateById(run);
        }
    }

    /** 自动周期结束后，同时完成运行记录和任务记录。 */
    private void complete(Long runId, Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        TestRun run = testRunMapper.selectById(runId);
        MeasurementTask task = taskMapper.selectById(taskId);
        if (run != null) {
            run.setState("COMPLETED");
            run.setProcessStage("COMPLETED");
            run.setEndedAt(now);
            testRunMapper.updateById(run);
        }
        if (task != null) {
            task.setTaskStatus("COMPLETED");
            task.setCompletedAt(now);
            taskMapper.updateById(task);
        }
    }

    private BigDecimal positiveOr(BigDecimal value, BigDecimal fallback) {
        return value != null && value.signum() > 0 ? value : fallback;
    }

    private AutomaticProcessSession requireSession(Long runId) {
        AutomaticProcessSession session = sessions.get(runId);
        if (session == null) {
            throw new IllegalStateException("模拟运行会话不存在");
        }
        return session;
    }

    private ManualSession requireManualSession(String pointCode) {
        ManualSession session = manualSessions.get(pointCode);
        if (session == null) {
            throw new IllegalStateException("手动设备动作不存在");
        }
        return session;
    }

    private void dispatchManual(ManualSession session) {
        switch (session.action) {
            case ASCENDING -> deviceCommandGateway.sendAscending(session.pointCode);
            case DESCENDING -> deviceCommandGateway.sendDescending(session.pointCode);
            case MANUAL_QUICK_RELEASE -> deviceCommandGateway.sendQuickRelease(session.pointCode);
            default -> throw new IllegalArgumentException("不支持的手动动作：" + session.action);
        }
    }

    /** 单个通道当前手动设备动作；不对应数据库任务或运行记录。 */
    private static final class ManualSession {

        private final String pointCode;
        private final ControlDto.ChannelAction action;
        private final BigDecimal targetDisplacementMm;
        private final BigDecimal initialDisplacementMm;
        private final BigDecimal initialForceKn;
        private final long startedAtMillis;
        private boolean paused;
        private long pausedAtMillis;
        private long accumulatedPauseMillis;

        private ManualSession(String pointCode, ControlDto.ChannelAction action,
                              BigDecimal targetDisplacementMm, BigDecimal initialDisplacementMm,
                              BigDecimal initialForceKn, long startedAtMillis) {
            this.pointCode = pointCode;
            this.action = action;
            this.targetDisplacementMm = targetDisplacementMm;
            this.initialDisplacementMm = initialDisplacementMm;
            this.initialForceKn = initialForceKn;
            this.startedAtMillis = startedAtMillis;
        }

        private void pause(long nowMillis) {
            paused = true;
            pausedAtMillis = nowMillis;
        }

        private void resume(long nowMillis) {
            accumulatedPauseMillis += Math.max(0L, nowMillis - pausedAtMillis);
            paused = false;
            pausedAtMillis = 0L;
        }

        private long activeElapsed(long nowMillis) {
            long effectiveNow = paused ? pausedAtMillis : nowMillis;
            return Math.max(0L, effectiveNow - startedAtMillis - accumulatedPauseMillis);
        }
    }

    /** 跨手动动作保留同一通道模拟传感器值，使上升后可继续下降或快泄。 */
    private static final class ManualSensorState {

        private BigDecimal displacementMm = BigDecimal.ZERO.setScale(2);
        private BigDecimal forceKn = BigDecimal.ZERO.setScale(1);
    }
}
