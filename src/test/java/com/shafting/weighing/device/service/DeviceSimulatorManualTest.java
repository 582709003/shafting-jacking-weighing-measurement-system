package com.shafting.weighing.device.service;

import com.shafting.weighing.control.dto.ControlDto;
import com.shafting.weighing.control.service.ControlStateService;
import com.shafting.weighing.device.config.SimulatorProperties;
import com.shafting.weighing.device.dto.RealtimeSnapshot;
import com.shafting.weighing.device.mapper.MeasurementSampleMapper;
import com.shafting.weighing.task.mapper.MeasurementTaskMapper;
import com.shafting.weighing.task.mapper.TestRunMapper;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

        DeviceSimulator simulator = new DeviceSimulator(
                mock(RealtimeStateStore.class), publisher, sampleMapper, testRunMapper,
                taskMapper, new SimulatorProperties(), stateService, gateway);
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
        verify(sampleMapper, never()).insert(any());
        verifyNoInteractions(testRunMapper, taskMapper);

        ArgumentCaptor<RealtimeSnapshot> snapshots = ArgumentCaptor.forClass(RealtimeSnapshot.class);
        verify(publisher, org.mockito.Mockito.times(3)).publish(snapshots.capture());
        assertThat(snapshots.getAllValues().get(1).displacementMm()).isEqualByComparingTo("0.03");
        assertThat(snapshots.getAllValues().get(2).state()).isEqualTo("STOPPED");
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

    private SensorReading reading(String displacementMm) {
        return new SensorReading(new BigDecimal(displacementMm), new BigDecimal("12.3"),
                800, Instant.parse("2026-11-17T08:00:00Z"));
    }
}
