package com.shafting.weighing.device.service;

import com.shafting.weighing.control.service.ControlStateService;
import com.shafting.weighing.device.config.SimulatorProperties;
import com.shafting.weighing.device.dto.SimulationStart;
import com.shafting.weighing.device.entity.MeasurementSample;
import com.shafting.weighing.device.mapper.MeasurementSampleMapper;
import com.shafting.weighing.task.mapper.MeasurementTaskMapper;
import com.shafting.weighing.task.mapper.TestRunMapper;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeviceSimulatorAutomaticTest {

    @Test
    void shouldPersistOnlyStablePeakAndFinalZeroSamples() throws Exception {
        SimulatorProperties properties = new SimulatorProperties();
        properties.setEnabled(true);
        properties.setPrecheckMs(0);
        properties.setLiftingMs(1);
        properties.setLoweringMs(1);
        MeasurementSampleMapper sampleMapper = mock(MeasurementSampleMapper.class);
        DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
        DeviceSimulator simulator = new DeviceSimulator(
                mock(RealtimeStateStore.class), mock(RealtimePublisher.class), sampleMapper,
                mock(TestRunMapper.class), mock(MeasurementTaskMapper.class), properties,
                mock(ControlStateService.class), gateway);
        simulator.start(new SimulationStart(1L, 2L, 3L, "TAIL_SHAFT",
                new BigDecimal("0.40"), 1_200, new BigDecimal("0.5"),
                new BigDecimal("25"), 0));

        simulator.tick();
        Thread.sleep(2L);
        simulator.tick();
        simulator.tick();
        Thread.sleep(2L);
        simulator.tick();

        ArgumentCaptor<MeasurementSample> samples = ArgumentCaptor.forClass(MeasurementSample.class);
        verify(sampleMapper, org.mockito.Mockito.times(2)).insert(samples.capture());
        assertThat(samples.getAllValues()).extracting(MeasurementSample::getStage)
                .containsExactly("LIFTING", "LOWERING");
        assertThat(samples.getAllValues().get(0).getDisplacementMm()).isEqualByComparingTo("0.40");
        assertThat(samples.getAllValues().get(1).getForceKn()).isEqualByComparingTo("0.0");
        verify(gateway).sendAscending("TAIL_SHAFT");
        verify(gateway).sendPause("TAIL_SHAFT");
        verify(gateway).sendDescending("TAIL_SHAFT");
        verify(gateway).sendStop("TAIL_SHAFT");
        verify(gateway, atLeastOnce()).readSensorValues("TAIL_SHAFT");
    }
}
