package com.shafting.weighing.device.service;

import com.shafting.weighing.control.dto.ControlDto;
import com.shafting.weighing.control.service.ControlStateService;
import com.shafting.weighing.device.config.SimulatorProperties;
import com.shafting.weighing.device.dto.RealtimeSnapshot;
import com.shafting.weighing.device.entity.MeasurementSample;
import com.shafting.weighing.device.mapper.MeasurementSampleMapper;
import com.shafting.weighing.task.mapper.MeasurementTaskMapper;
import com.shafting.weighing.task.mapper.TestRunMapper;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeviceSimulatorManualTest {

    @Test
    void shouldUseSensorReadingAndStopWithoutPersistingManualData() {
        ControlStateService stateService = new ControlStateService();
        stateService.connectMode("TAIL_SHAFT", ControlDto.ControlMode.MANUAL);
        stateService.startManual("TAIL_SHAFT");
        stateService.markZeroed("TAIL_SHAFT");
        stateService.beginAction("TAIL_SHAFT", ControlDto.ChannelAction.ASCENDING,
                new BigDecimal("0.04"), true);

        MeasurementSampleMapper sampleMapper = mock(MeasurementSampleMapper.class);
        TestRunMapper testRunMapper = mock(TestRunMapper.class);
        MeasurementTaskMapper taskMapper = mock(MeasurementTaskMapper.class);
        RealtimePublisher publisher = mock(RealtimePublisher.class);
        DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
        when(gateway.readSensorValues("TAIL_SHAFT"))
                .thenReturn(null)
                .thenReturn(reading("0.03"))
                .thenReturn(reading("0.04"));

        SimulatorProperties properties = new SimulatorProperties();
        properties.setManualEnabled(false);
        DeviceSimulator simulator = new DeviceSimulator(
                mock(RealtimeStateStore.class), publisher, sampleMapper, testRunMapper,
                taskMapper, properties, stateService, gateway);
        simulator.startManualAction("TAIL_SHAFT", ControlDto.ChannelAction.ASCENDING,
                new BigDecimal("0.04"));

        simulator.tick();
        assertThat(stateService.channel("TAIL_SHAFT").action())
                .isEqualTo(ControlDto.ChannelAction.ASCENDING);
        simulator.tick();
        assertThat(stateService.channel("TAIL_SHAFT").action())
                .isEqualTo(ControlDto.ChannelAction.ASCENDING);
        simulator.tick();

        assertThat(stateService.channel("TAIL_SHAFT").action()).isEqualTo(ControlDto.ChannelAction.IDLE);
        assertThat(stateService.channel("TAIL_SHAFT").runId()).isNull();
        verify(gateway).sendAscending("TAIL_SHAFT");
        verify(gateway).sendStop("TAIL_SHAFT");
        verify(sampleMapper, never()).insert(any(MeasurementSample.class));
        verifyNoInteractions(testRunMapper, taskMapper);

        ArgumentCaptor<RealtimeSnapshot> snapshots = ArgumentCaptor.forClass(RealtimeSnapshot.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(snapshots.capture());
        RealtimeSnapshot realtime = snapshots.getAllValues().get(0);
        assertThat(realtime.displacementMm()).isEqualByComparingTo("0.03");
        assertThat(realtime.forceKn()).isEqualByComparingTo("12.3");
        assertThat(realtime.motorRpm()).isEqualTo(800);
        assertThat(snapshots.getAllValues().get(1).state()).isEqualTo("STOPPED");
    }

    @Test
    void shouldGenerateTimeBasedManualSensorReadingWithoutHardware() throws Exception {
        ControlStateService stateService = new ControlStateService();
        stateService.connectMode("TAIL_SHAFT", ControlDto.ControlMode.MANUAL);
        stateService.startManual("TAIL_SHAFT");
        stateService.markZeroed("TAIL_SHAFT");
        stateService.beginAction("TAIL_SHAFT", ControlDto.ChannelAction.ASCENDING,
                new BigDecimal("0.40"), true);
        RealtimePublisher publisher = mock(RealtimePublisher.class);
        SimulatorProperties properties = new SimulatorProperties();
        properties.setManualEnabled(true);
        properties.setLiftingMs(20);
        DeviceSimulator simulator = new DeviceSimulator(
                mock(RealtimeStateStore.class), publisher, mock(MeasurementSampleMapper.class),
                mock(TestRunMapper.class), mock(MeasurementTaskMapper.class), properties,
                stateService, new DeviceCommandGateway());

        simulator.zeroManualSimulation("TAIL_SHAFT");
        simulator.startManualAction("TAIL_SHAFT", ControlDto.ChannelAction.ASCENDING,
                new BigDecimal("0.40"));
        Thread.sleep(25);
        simulator.tick();

        ArgumentCaptor<RealtimeSnapshot> snapshots = ArgumentCaptor.forClass(RealtimeSnapshot.class);
        verify(publisher).publish(snapshots.capture());
        RealtimeSnapshot snapshot = snapshots.getValue();
        assertThat(snapshot.displacementMm()).isEqualByComparingTo("0.40");
        assertThat(snapshot.forceKn()).isEqualByComparingTo("180.0");
        assertThat(snapshot.motorRpm()).isEqualTo(800);
        assertThat(snapshot.state()).isEqualTo("STOPPED");
    }

    @Test
    void shouldPauseResumeAndQuickReleaseThroughDeviceGateway() {
        ControlStateService stateService = new ControlStateService();
        DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
        DeviceSimulator simulator = new DeviceSimulator(
                mock(RealtimeStateStore.class), mock(RealtimePublisher.class),
                mock(MeasurementSampleMapper.class), mock(TestRunMapper.class),
                mock(MeasurementTaskMapper.class), new SimulatorProperties(), stateService, gateway);

        simulator.startManualAction("TAIL_SHAFT", ControlDto.ChannelAction.MANUAL_QUICK_RELEASE, null);
        simulator.pauseManual("TAIL_SHAFT");
        simulator.resumeManual("TAIL_SHAFT");
        simulator.stopManual("TAIL_SHAFT");

        verify(gateway, org.mockito.Mockito.times(2)).sendQuickRelease("TAIL_SHAFT");
        verify(gateway).sendPause("TAIL_SHAFT");
        verify(gateway).sendStop("TAIL_SHAFT");
    }

    @Test
    void shouldPublishDecreasingRealtimeValuesDuringAutomaticQuickRelease() throws Exception {
        ControlStateService stateService = new ControlStateService();
        RealtimePublisher publisher = mock(RealtimePublisher.class);
        MeasurementSampleMapper sampleMapper = mock(MeasurementSampleMapper.class);
        DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
        SimulatorProperties properties = new SimulatorProperties();
        properties.setLiftingMs(1);
        properties.setLoweringMs(3_000);
        DeviceSimulator simulator = new DeviceSimulator(
                mock(RealtimeStateStore.class), publisher, sampleMapper, mock(TestRunMapper.class),
                mock(MeasurementTaskMapper.class), properties, stateService, gateway);

        simulator.startManualAction("TAIL_SHAFT", ControlDto.ChannelAction.ASCENDING,
                new BigDecimal("0.40"));
        Thread.sleep(5);
        simulator.tick();
        clearInvocations(publisher, gateway);

        simulator.startAutoQuickRelease(List.of("TAIL_SHAFT"));
        Thread.sleep(30);
        simulator.tick();

        ArgumentCaptor<RealtimeSnapshot> snapshots = ArgumentCaptor.forClass(RealtimeSnapshot.class);
        verify(publisher).publish(snapshots.capture());
        RealtimeSnapshot snapshot = snapshots.getValue();
        assertThat(snapshot.pointCode()).isEqualTo("TAIL_SHAFT");
        assertThat(snapshot.processStage()).isEqualTo("LOWERING");
        assertThat(snapshot.state()).isEqualTo("RUNNING");
        assertThat(snapshot.displacementMm()).isBetween(new BigDecimal("0.01"), new BigDecimal("0.39"));
        assertThat(snapshot.forceKn()).isBetween(new BigDecimal("0.1"), new BigDecimal("179.9"));
        verify(gateway).sendQuickRelease("TAIL_SHAFT");
        verify(sampleMapper, never()).insert(any(MeasurementSample.class));
    }

    private SensorReading reading(String displacementMm) {
        return new SensorReading(new BigDecimal(displacementMm), new BigDecimal("12.3"),
                800, Instant.parse("2026-11-17T08:00:00Z"));
    }
}
