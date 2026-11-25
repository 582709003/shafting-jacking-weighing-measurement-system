package com.shafting.weighing.device.service;

import com.shafting.weighing.device.config.SimulatorProperties;
import com.shafting.weighing.device.dto.RealtimeSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * 单通道自动顶升测量状态机。
 *
 * <p>上升和下降均按压力间隔暂停；配置的保持时间结束后才产生一组
 * 位移和顶升力采样。上升以最大位移结束，下降以压力归零结束。</p>
 */
final class AutomaticProcessSession {

    /** PLC 压力模拟量的量程上限；仅用于无设备时的线性流程模拟。 */
    private static final BigDecimal SIMULATED_MAX_PRESSURE_MPA = new BigDecimal("100");

    private final Long taskId;
    private final Long pointId;
    private final String pointCode;
    private final SimulationLimits limits;
    private final long liftingMillis;
    private final long loweringMillis;
    private final long holdMillis;
    private long sequence;
    private long lastTickMillis;
    private long phaseDeadlineMillis;
    private long pausedAtMillis;
    private Phase phase = Phase.PRECHECK;
    private String state = "RUNNING";
    private String pendingSampleStage;
    private boolean maximumDisplacementReached;
    private BigDecimal nextAscendingThresholdMpa;
    private BigDecimal nextDescendingThresholdMpa;
    private BigDecimal pressureMpa = BigDecimal.ZERO;
    private BigDecimal displacementMm = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private BigDecimal forceKn = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
    private int motorRpm;
    private boolean deviceReadingAvailable;
    private Instant capturedAt = Instant.now();

    AutomaticProcessSession(Long taskId, Long pointId, String pointCode, SimulationLimits limits,
                            SimulatorProperties properties) {
        this.taskId = taskId;
        this.pointId = pointId;
        this.pointCode = pointCode;
        this.limits = limits;
        liftingMillis = Math.max(1L, properties.getLiftingMs());
        loweringMillis = Math.max(1L, properties.getLoweringMs());
        holdMillis = Math.max(0L, limits.intervalHoldMillis());
        lastTickMillis = System.currentTimeMillis();
        phaseDeadlineMillis = lastTickMillis + Math.max(0L, properties.getPrecheckMs());
        nextAscendingThresholdMpa = limits.pressureIntervalMpa();
    }

    /** 推进一步自动流程，并返回本次需要下发的设备动作和可保存的稳定采样阶段。 */
    AutomaticStep advance(long nowMillis, SensorReading reading) {
        // 实时数据不受操作暂停影响，暂停期间仍需持续刷新工作页传感器显示。
        applyReading(reading);
        if (!"RUNNING".equals(state)) {
            lastTickMillis = nowMillis;
            return step(DeviceAction.NONE, null);
        }
        long elapsedMillis = Math.max(0L, nowMillis - lastTickMillis);
        lastTickMillis = nowMillis;
        return switch (phase) {
            case PRECHECK -> startAscendingWhenReady(nowMillis);
            case LIFTING -> advanceLifting(nowMillis, elapsedMillis, reading == null);
            case HOLDING -> finishHoldingWhenReady(nowMillis);
            case LOWERING -> advanceLowering(nowMillis, elapsedMillis, reading == null);
            case COMPLETED -> step(DeviceAction.NONE, null);
        };
    }

    /** 操作员暂停时冻结预检或内部保持计时，并保留当前阶段。 */
    void pause(long nowMillis) {
        if (!"PAUSED".equals(state)) {
            state = "PAUSED";
            pausedAtMillis = nowMillis;
        }
    }

    /** 操作员继续时恢复被冻结的计时，返回当前阶段需要恢复的设备动作。 */
    DeviceAction resume(long nowMillis) {
        if (!"PAUSED".equals(state)) {
            return DeviceAction.NONE;
        }
        long pausedMillis = Math.max(0L, nowMillis - pausedAtMillis);
        if (phase == Phase.PRECHECK || phase == Phase.HOLDING) {
            phaseDeadlineMillis += pausedMillis;
        }
        state = "RUNNING";
        lastTickMillis = nowMillis;
        return phase == Phase.LIFTING ? DeviceAction.ASCEND
                : phase == Phase.LOWERING ? DeviceAction.DESCEND : DeviceAction.NONE;
    }

    /** 返回停止或暂停推送使用的当前快照。 */
    RealtimeSnapshot snapshot(Long runId, String snapshotState) {
        int rpm = deviceReadingAvailable ? motorRpm : simulatedMotorRpm();
        return new RealtimeSnapshot(runId, taskId, pointCode, snapshotState, phase.name(),
                displacementMm, rpm, forceKn, capturedAt);
    }

    private AutomaticStep startAscendingWhenReady(long nowMillis) {
        if (nowMillis < phaseDeadlineMillis) {
            return step(DeviceAction.NONE, null);
        }
        phase = Phase.LIFTING;
        return step(DeviceAction.ASCEND, null);
    }

    private AutomaticStep advanceLifting(long nowMillis, long elapsedMillis, boolean simulated) {
        if (simulated) {
            applyRatio(currentRatio().add(durationRatio(elapsedMillis, liftingMillis)).min(BigDecimal.ONE));
        }
        if (displacementMm.compareTo(limits.maxDisplacementMm()) >= 0) {
            if (simulated) {
                applyRatio(BigDecimal.ONE);
            }
            return beginHolding(nowMillis, "LIFTING", true);
        }
        if (pressureMpa.compareTo(nextAscendingThresholdMpa) >= 0) {
            if (simulated) {
                applyPressureThreshold(nextAscendingThresholdMpa);
            }
            return beginHolding(nowMillis, "LIFTING", false);
        }
        return step(DeviceAction.NONE, null);
    }

    private AutomaticStep advanceLowering(long nowMillis, long elapsedMillis, boolean simulated) {
        if (simulated) {
            applyRatio(currentRatio().subtract(durationRatio(elapsedMillis, loweringMillis))
                    .max(BigDecimal.ZERO));
        }
        if (pressureMpa.signum() <= 0) {
            if (simulated) {
                applyRatio(BigDecimal.ZERO);
            }
            phase = Phase.COMPLETED;
            state = "COMPLETED";
            // 下降归零必须明确停止液压动作，并将当前即时值作为最后一组下降采样保存。
            return step(DeviceAction.STOP, "LOWERING");
        }
        if (nextDescendingThresholdMpa != null && nextDescendingThresholdMpa.signum() > 0
                && pressureMpa.compareTo(nextDescendingThresholdMpa) <= 0) {
            if (simulated) {
                applyPressureThreshold(nextDescendingThresholdMpa);
            }
            return beginHolding(nowMillis, "LOWERING", false);
        }
        return step(DeviceAction.NONE, null);
    }

    private AutomaticStep beginHolding(long nowMillis, String sampleStage, boolean reachedMaximum) {
        phase = Phase.HOLDING;
        phaseDeadlineMillis = nowMillis + holdMillis;
        pendingSampleStage = sampleStage;
        maximumDisplacementReached = reachedMaximum;
        return step(DeviceAction.PAUSE, null);
    }

    private AutomaticStep finishHoldingWhenReady(long nowMillis) {
        if (nowMillis < phaseDeadlineMillis) {
            return step(DeviceAction.NONE, null);
        }
        String sampleStage = pendingSampleStage;
        if (maximumDisplacementReached) {
            nextDescendingThresholdMpa = pressureMpa.subtract(limits.pressureIntervalMpa());
            phase = Phase.LOWERING;
            maximumDisplacementReached = false;
            return step(DeviceAction.DESCEND, sampleStage);
        }
        if ("LIFTING".equals(sampleStage)) {
            nextAscendingThresholdMpa = nextAscendingThresholdMpa.add(limits.pressureIntervalMpa());
            phase = Phase.LIFTING;
            return step(DeviceAction.ASCEND, sampleStage);
        }
        nextDescendingThresholdMpa = nextDescendingThresholdMpa.subtract(limits.pressureIntervalMpa());
        phase = Phase.LOWERING;
        return step(DeviceAction.DESCEND, sampleStage);
    }

    private void applyReading(SensorReading reading) {
        if (reading == null) {
            deviceReadingAvailable = false;
            capturedAt = Instant.now();
            return;
        }
        deviceReadingAvailable = true;
        displacementMm = reading.displacementMm();
        forceKn = reading.forceKn();
        BigDecimal ratio = limits.maxDisplacementMm().signum() == 0 ? BigDecimal.ZERO
                : displacementMm.divide(limits.maxDisplacementMm(), 12, RoundingMode.HALF_UP);
        pressureMpa = SIMULATED_MAX_PRESSURE_MPA.multiply(
                ratio.max(BigDecimal.ZERO).min(BigDecimal.ONE));
        motorRpm = reading.motorRpm();
        capturedAt = reading.capturedAt();
    }

    /** 尚未接入设备时，按模拟流程阶段生成工作页电机转速。 */
    private int simulatedMotorRpm() {
        return phase == Phase.LIFTING || phase == Phase.LOWERING ? limits.motorRpm() : 0;
    }

    private void applyPressureThreshold(BigDecimal thresholdMpa) {
        BigDecimal ratio = thresholdMpa.divide(SIMULATED_MAX_PRESSURE_MPA, 12, RoundingMode.HALF_UP);
        applyRatio(ratio.max(BigDecimal.ZERO).min(BigDecimal.ONE));
    }

    private void applyRatio(BigDecimal ratio) {
        displacementMm = limits.maxDisplacementMm().multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        forceKn = limits.maxForceKn().multiply(ratio).setScale(1, RoundingMode.HALF_UP);
        pressureMpa = SIMULATED_MAX_PRESSURE_MPA.multiply(ratio);
        capturedAt = Instant.now();
    }

    private BigDecimal currentRatio() {
        return pressureMpa.divide(SIMULATED_MAX_PRESSURE_MPA, 12, RoundingMode.HALF_UP);
    }

    private BigDecimal durationRatio(long elapsedMillis, long durationMillis) {
        return BigDecimal.valueOf(elapsedMillis)
                .divide(BigDecimal.valueOf(durationMillis), 12, RoundingMode.HALF_UP);
    }

    private AutomaticStep step(DeviceAction action, String sampleStage) {
        return new AutomaticStep(action, sampleStage, "COMPLETED".equals(state));
    }

    Long taskId() {
        return taskId;
    }

    Long pointId() {
        return pointId;
    }

    String pointCode() {
        return pointCode;
    }

    long nextSequence() {
        return ++sequence;
    }

    String state() {
        return state;
    }

    private enum Phase {
        PRECHECK,
        LIFTING,
        HOLDING,
        LOWERING,
        COMPLETED
    }
}

/** 自动状态机单次推进后需要执行的设备动作和采样动作。 */
record AutomaticStep(
        /** 本次需要下发的设备动作。 */
        DeviceAction deviceAction,
        /** 需要保存的采样阶段；为空表示本次不落库。 */
        String sampleStage,
        /** 是否已经完成下降归零。 */
        boolean completed) {
}

/** 自动状态机对设备网关的动作；NONE 表示本次无需下发。 */
enum DeviceAction {
    NONE,
    ASCEND,
    PAUSE,
    DESCEND,
    STOP
}

/** 一次自动运行使用的目标值和模拟器参数。 */
record SimulationLimits(
        /** 自动测试最大位移，单位 mm。 */
        BigDecimal maxDisplacementMm,
        /** 模拟电机转速，单位 r/min。 */
        int motorRpm,
        /** 自动测试模拟峰值顶升力，单位 kN。 */
        BigDecimal maxForceKn,
        /** 自动测试压力间隔，单位 MPa。 */
        BigDecimal pressureIntervalMpa,
        /** 每次暂停保持时间，单位毫秒。 */
        long intervalHoldMillis) {
}
