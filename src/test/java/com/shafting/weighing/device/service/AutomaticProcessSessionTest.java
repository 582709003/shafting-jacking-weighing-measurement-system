package com.shafting.weighing.device.service;

import com.shafting.weighing.device.config.SimulatorProperties;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AutomaticProcessSessionTest {

    @Test
    void shouldCalculateDescendingIntervalFromActualPeakForce() {
        SimulatorProperties properties = new SimulatorProperties();
        properties.setPrecheckMs(0);
        SimulationLimits limits = new SimulationLimits(
                new BigDecimal("0.40"), 1_200,
                new BigDecimal("2.0"), new BigDecimal("0.5"), 0);
        AutomaticProcessSession session = new AutomaticProcessSession(
                1L, 1L, "TAIL_SHAFT", limits, properties);
        long now = System.currentTimeMillis() + 10;

        assertThat(session.advance(now, null).deviceAction()).isEqualTo(DeviceAction.ASCEND);
        SensorReading peak = reading("0.40", "1.2", now);
        assertThat(session.advance(++now, peak).deviceAction()).isEqualTo(DeviceAction.PAUSE);
        assertThat(session.advance(++now, peak).deviceAction()).isEqualTo(DeviceAction.DESCEND);

        // 实际峰值为 1.2kN，首个下降暂停点应为 0.7kN，而不是配置标准力推导出的 1.5kN。
        assertThat(session.advance(++now, reading("0.30", "0.8", now)).deviceAction())
                .isEqualTo(DeviceAction.NONE);
        assertThat(session.advance(++now, reading("0.20", "0.7", now)).deviceAction())
                .isEqualTo(DeviceAction.PAUSE);
    }

    @Test
    void shouldRefreshLiveSensorValuesWhileOperatorPaused() {
        SimulatorProperties properties = new SimulatorProperties();
        properties.setPrecheckMs(0);
        SimulationLimits limits = new SimulationLimits(
                new BigDecimal("0.40"), 1_200,
                new BigDecimal("2.0"), new BigDecimal("0.5"), 100);
        AutomaticProcessSession session = new AutomaticProcessSession(
                1L, 1L, "TAIL_SHAFT", limits, properties);
        long now = System.currentTimeMillis() + 10;

        session.advance(now, null);
        session.pause(now);
        Instant capturedAt = Instant.parse("2026-11-16T08:00:00Z");
        SensorReading reading = new SensorReading(
                new BigDecimal("0.12"), new BigDecimal("0.8"), 321, capturedAt);

        AutomaticStep step = session.advance(now + 100, reading);
        var snapshot = session.snapshot(1L, session.state());

        assertThat(step.sampleStage()).isNull();
        assertThat(snapshot.displacementMm()).isEqualByComparingTo("0.12");
        assertThat(snapshot.forceKn()).isEqualByComparingTo("0.8");
        assertThat(snapshot.motorRpm()).isEqualTo(321);
        assertThat(snapshot.capturedAt()).isEqualTo(capturedAt);
    }

    @Test
    void shouldPauseAndSampleAtEveryFiveHundredNewtonIntervalInBothDirections() {
        SimulatorProperties properties = new SimulatorProperties();
        properties.setPrecheckMs(0);
        properties.setLiftingMs(1_000);
        properties.setLoweringMs(1_000);
        SimulationLimits limits = new SimulationLimits(
                new BigDecimal("0.40"), 1_200,
                new BigDecimal("2.0"), new BigDecimal("0.5"), 100);
        AutomaticProcessSession session = new AutomaticProcessSession(
                1L, 1L, "TAIL_SHAFT", limits, properties);
        long now = System.currentTimeMillis() + 10;

        assertThat(session.advance(now, null).deviceAction()).isEqualTo(DeviceAction.ASCEND);

        AutomaticStep firstPause = session.advance(now += 250, null);
        assertThat(firstPause.deviceAction()).isEqualTo(DeviceAction.PAUSE);
        assertThat(session.snapshot(1L, session.state()).forceKn()).isEqualByComparingTo("0.5");
        AutomaticStep firstSample = session.advance(now += 100, null);
        assertThat(firstSample.sampleStage()).isEqualTo("LIFTING");
        assertThat(firstSample.deviceAction()).isEqualTo(DeviceAction.ASCEND);

        AutomaticStep secondPause = session.advance(now += 250, null);
        assertThat(secondPause.deviceAction()).isEqualTo(DeviceAction.PAUSE);
        assertThat(session.snapshot(1L, session.state()).forceKn()).isEqualByComparingTo("1.0");

        // 完成 1.0kN、1.5kN 和最高位移三次保持后进入下降。
        session.advance(now += 100, null);
        session.advance(now += 250, null);
        session.advance(now += 100, null);
        session.advance(now += 250, null);
        AutomaticStep peakSample = session.advance(now += 100, null);
        assertThat(peakSample.sampleStage()).isEqualTo("LIFTING");
        assertThat(peakSample.deviceAction()).isEqualTo(DeviceAction.DESCEND);
        assertThat(session.snapshot(1L, session.state()).displacementMm()).isEqualByComparingTo("0.40");

        AutomaticStep descendingPause = session.advance(now += 250, null);
        assertThat(descendingPause.deviceAction()).isEqualTo(DeviceAction.PAUSE);
        assertThat(session.snapshot(1L, session.state()).forceKn()).isEqualByComparingTo("1.5");

        session.advance(now += 100, null);
        session.advance(now += 250, null);
        session.advance(now += 100, null);
        session.advance(now += 250, null);
        session.advance(now += 100, null);
        AutomaticStep completed = session.advance(now += 250, null);
        assertThat(completed.completed()).isTrue();
        assertThat(completed.deviceAction()).isEqualTo(DeviceAction.STOP);
        assertThat(completed.sampleStage()).isEqualTo("LOWERING");
        assertThat(session.snapshot(1L, session.state()).forceKn()).isEqualByComparingTo("0.0");
    }

    private SensorReading reading(String displacementMm, String forceKn, long capturedAt) {
        return new SensorReading(new BigDecimal(displacementMm), new BigDecimal(forceKn),
                1_200, Instant.ofEpochMilli(capturedAt));
    }
}
