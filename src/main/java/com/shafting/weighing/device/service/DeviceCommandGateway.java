package com.shafting.weighing.device.service;

import com.shafting.weighing.control.dto.ControlDto;
import com.shafting.weighing.device.config.PlcProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作页、设置页与现场 S7-1200 之间的设备命令边界。
 *
 * <p>SIMULATOR 模式只保留调用边界，由 {@link DeviceSimulator} 产生联调数据；PLC 模式下本类只负责
 * 写入点位、等待 PLC 反馈和读取传感器，不在 Java 中编排自动顶升动作。</p>
 */
@Component
public class DeviceCommandGateway {

    private static final BigDecimal ZERO_TOLERANCE_MM = new BigDecimal("0.01");
    private static final BigDecimal REAL_TOLERANCE = new BigDecimal("0.001");

    private final PlcProperties properties;
    private final S7PlcClient plcClient;
    private final Set<String> unsynchronizedManualTargets = ConcurrentHashMap.newKeySet();

    public DeviceCommandGateway(PlcProperties properties, S7PlcClient plcClient) {
        this.properties = properties;
        this.plcClient = plcClient;
    }

    /**
     * 每次接通模式都重新选择页面当前通道、同步数据库参数并确认模式。
     *
     * <p>设置页只保存数据库，因此这里必须在模式脉冲前完成参数写入和回读；任一步失败都不得继续接通。</p>
     */
    public void connectMode(String pointCode, ControlDto.ControlMode mode, PointConfiguration configuration) {
        if (!properties.isPlcMode()) {
            return;
        }
        if (mode == ControlDto.ControlMode.MANUAL && unsynchronizedManualTargets.contains(pointCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "手动目标位移与 PLC 未同步，请重新保存当前通道手动目标位移");
        }
        PlcPointCatalog.ChannelTags channel = PlcPointCatalog.channel(pointCode);
        plcClient.pulse(channel.selection());
        plcClient.awaitInt(properties.getFeedbackSelectedChannel(), channel.channelValue(),
                properties.getFeedbackTimeoutMs());
        writePointConfiguration(pointCode, configuration);
        plcClient.pulse(PlcPointCatalog.control(modeControl(mode)));
        plcClient.awaitInt(properties.getFeedbackMode(), mode == ControlDto.ControlMode.MANUAL ? 1 : 2,
                properties.getFeedbackTimeoutMs());
    }

    /** 断开当前模式时不重复选择通道，直接切换模式并等待 PLC 返回断开。 */
    public void disconnectMode(ControlDto.ControlMode mode) {
        if (!properties.isPlcMode()) {
            return;
        }
        plcClient.pulse(PlcPointCatalog.control(modeControl(mode)));
        plcClient.awaitInt(properties.getFeedbackMode(), 0, properties.getFeedbackTimeoutMs());
    }

    /** 向当前 PLC 已选通道发送手动启动命令。 */
    public void sendManualStart(String pointCode) {
        pulseAction(PlcPointCatalog.Control.START, 1);
    }

    /** 向指定通道独立清零点发送脉冲，并确认位移达到 0.00±0.01 mm。 */
    public void sendDisplacementZero(String pointCode) {
        if (!properties.isPlcMode()) {
            return;
        }
        String zeroAddress = requireConfiguredAddress(properties.getZeroDisplacement(), pointCode, "位移清零");
        plcClient.pulse(zeroAddress);
        plcClient.awaitDecimal(PlcPointCatalog.channel(pointCode).displacement(), BigDecimal.ZERO,
                ZERO_TOLERANCE_MM, properties.getFeedbackTimeoutMs());
    }

    /** 向当前 PLC 已选通道下发上升脉冲并确认动作反馈。 */
    public void sendAscending(String pointCode) {
        pulseAction(PlcPointCatalog.Control.ASCEND, 2);
    }

    /** 向当前 PLC 已选通道下发暂停脉冲并确认暂停反馈。 */
    public void sendPause(String pointCode) {
        pulseRunState(PlcPointCatalog.Control.PAUSE, 2);
    }

    /** 再次发送暂停/继续脉冲，并确认恢复运行。 */
    public void sendResume(String pointCode) {
        pulseRunState(PlcPointCatalog.Control.PAUSE, 1);
    }

    /** 向当前 PLC 已选通道下发下降脉冲并确认动作反馈。 */
    public void sendDescending(String pointCode) {
        pulseAction(PlcPointCatalog.Control.DESCEND, 3);
    }

    /** 向当前 PLC 已选通道下发手动快泄脉冲并确认动作反馈。 */
    public void sendQuickRelease(String pointCode) {
        pulseAction(PlcPointCatalog.Control.QUICK_RELEASE, 4);
    }

    /** 下发自动测试脉冲；PLC 确认运行后 Java 才把命令视为成功。 */
    public void sendAutomaticTest(String pointCode) {
        if (!properties.isPlcMode()) {
            return;
        }
        plcClient.pulse(PlcPointCatalog.control(PlcPointCatalog.Control.AUTO_TEST));
        plcClient.awaitInt(properties.getFeedbackRunState(), 1, properties.getFeedbackTimeoutMs());
    }

    /** 下发六通道自动快泄脉冲并确认动作反馈。 */
    public void sendAutomaticQuickRelease(String pointCode) {
        pulseAction(PlcPointCatalog.Control.AUTO_QUICK_RELEASE, 6);
    }

    /** 下发停止脉冲并确认 PLC 返回空闲。 */
    public void sendStop(String pointCode) {
        if (!properties.isPlcMode()) {
            return;
        }
        plcClient.pulse(PlcPointCatalog.control(PlcPointCatalog.Control.STOP));
        plcClient.awaitInt(properties.getFeedbackRunState(), 0, properties.getFeedbackTimeoutMs());
    }

    /** 同时读取指定通道的位移、顶升力和电机转速即时值。 */
    public SensorReading readSensorValues(String pointCode) {
        if (!properties.isPlcMode()) {
            return null;
        }
        PlcPointCatalog.ChannelTags channel = PlcPointCatalog.channel(pointCode);
        Map<String, BigDecimal> values = plcClient.readValues(Map.of(
                "displacement", channel.displacement(),
                "force", channel.force(),
                "motorRpm", channel.motorRpm()));
        return new SensorReading(values.get("displacement"), values.get("force"),
                values.get("motorRpm").intValue(), Instant.now());
    }

    /** 保存手动目标位移前先写入当前通道新增 REAL 点并回读一致。 */
    public void writeManualTarget(String pointCode, BigDecimal targetDisplacementMm) {
        if (!properties.isPlcMode()) {
            return;
        }
        String address = requireConfiguredAddress(properties.getManualTarget(), pointCode, "手动目标位移");
        try {
            writeAndVerify(Map.of(address, targetDisplacementMm.floatValue()),
                    Map.of(address, targetDisplacementMm));
            unsynchronizedManualTargets.remove(pointCode);
        } catch (RuntimeException exception) {
            unsynchronizedManualTargets.add(pointCode);
            throw exception;
        }
    }

    /** 接通模式前批量下发数据库中的当前通道参数并逐项回读；总值只保存数据库。 */
    public void writePointConfiguration(String pointCode, PointConfiguration values) {
        if (!properties.isPlcMode()) {
            return;
        }
        PlcPointCatalog.ChannelTags tags = PlcPointCatalog.channel(pointCode);
        Map<String, Object> writes = new LinkedHashMap<>();
        writes.put(tags.standardForce(), values.standardForce().floatValue());
        writes.put(tags.jackingCoefficient(), values.jackingCoefficient().floatValue());
        writes.put(tags.maxDisplacement(), values.maxDisplacement().floatValue());
        writes.put(tags.configuredMotorRpm(), values.motorRpm().floatValue());
        writes.put(tags.forceInterval(), values.forceIntervalKn());
        writes.put(tags.holdSeconds(), values.holdSeconds().floatValue());

        Map<String, BigDecimal> expected = new LinkedHashMap<>();
        expected.put(tags.standardForce(), values.standardForce());
        expected.put(tags.jackingCoefficient(), values.jackingCoefficient());
        expected.put(tags.maxDisplacement(), values.maxDisplacement());
        expected.put(tags.configuredMotorRpm(), values.motorRpm());
        expected.put(tags.forceInterval(), BigDecimal.valueOf(values.forceIntervalKn()));
        expected.put(tags.holdSeconds(), values.holdSeconds());
        writeAndVerify(writes, expected);
    }

    /** PLC 轮询服务读取反馈点时使用。 */
    public Map<String, BigDecimal> readFeedback(Map<String, String> addresses) {
        if (!properties.isPlcMode()) {
            return Map.of();
        }
        return plcClient.readValues(addresses);
    }

    /** 手动目标已写入 PLC 但数据库事务回滚时锁定手动模式，要求操作员重新保存。 */
    public void markManualTargetUnsynchronized(String pointCode) {
        if (properties.isPlcMode()) {
            unsynchronizedManualTargets.add(pointCode);
        }
    }

    private void writeAndVerify(Map<String, Object> writes, Map<String, BigDecimal> expectedValues) {
        plcClient.writeValues(writes);
        Map<String, String> reads = new LinkedHashMap<>();
        expectedValues.keySet().forEach(address -> reads.put(address, address));
        Map<String, BigDecimal> actualValues = plcClient.readValues(reads);
        for (Map.Entry<String, BigDecimal> expected : expectedValues.entrySet()) {
            BigDecimal tolerance = expected.getKey().endsWith(":INT") ? BigDecimal.ZERO : REAL_TOLERANCE;
            BigDecimal actual = actualValues.get(expected.getKey());
            if (actual == null || actual.subtract(expected.getValue()).abs().compareTo(tolerance) > 0) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "PLC 参数回读不一致：" + expected.getKey());
            }
        }
    }

    private void pulseAction(PlcPointCatalog.Control control, int expectedAction) {
        if (!properties.isPlcMode()) {
            return;
        }
        plcClient.pulse(PlcPointCatalog.control(control));
        plcClient.awaitInt(properties.getFeedbackAction(), expectedAction, properties.getFeedbackTimeoutMs());
    }

    private void pulseRunState(PlcPointCatalog.Control control, int expectedState) {
        if (!properties.isPlcMode()) {
            return;
        }
        plcClient.pulse(PlcPointCatalog.control(control));
        plcClient.awaitInt(properties.getFeedbackRunState(), expectedState, properties.getFeedbackTimeoutMs());
    }

    private PlcPointCatalog.Control modeControl(ControlDto.ControlMode mode) {
        return switch (mode) {
            case MANUAL -> PlcPointCatalog.Control.MANUAL;
            case AUTO -> PlcPointCatalog.Control.AUTO;
            case OFF -> throw new IllegalArgumentException("OFF 不是可发送的 PLC 模式命令");
        };
    }

    private String requireConfiguredAddress(Map<String, String> addresses, String pointCode, String businessName) {
        String address = addresses.get(pointCode);
        if (address == null || address.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PLC 未配置" + businessName + "点位：" + pointCode);
        }
        return address;
    }

    /** 接通手动或自动模式时需要写入 PLC 的当前通道参数，不含仅存数据库的总值。 */
    public record PointConfiguration(
            /** 标准值，单位 kN。 */ BigDecimal standardForce,
            /** 顶升计算系数。 */ BigDecimal jackingCoefficient,
            /** 自动目标位移，单位 mm。 */ BigDecimal maxDisplacement,
            /** 顶升速度，单位 r/min。 */ BigDecimal motorRpm,
            /** 力值间隔，单位 kN，范围 1～32767。 */ int forceIntervalKn,
            /** 间隔保持时间，单位秒。 */ BigDecimal holdSeconds) { }
}

/** 一次同时读取的通道即时值，用于工作页实时显示和 PLC 自动流程采样。 */
record SensorReading(
        /** 位移传感器读数，单位 mm、精度 0.01。 */ BigDecimal displacementMm,
        /** 称重传感器顶升力，单位 kN、精度 0.1。 */ BigDecimal forceKn,
        /** 电机转速即时值，单位 r/min。 */ int motorRpm,
        /** 各项读数的统一采集时间。 */ Instant capturedAt) { }
