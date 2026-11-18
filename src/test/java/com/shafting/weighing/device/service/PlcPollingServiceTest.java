package com.shafting.weighing.device.service;

import com.shafting.weighing.alarm.service.AlarmService;
import com.shafting.weighing.control.dto.ControlDto;
import com.shafting.weighing.control.service.ControlStateService;
import com.shafting.weighing.device.config.PlcProperties;
import com.shafting.weighing.device.entity.MeasurementSample;
import com.shafting.weighing.device.mapper.MeasurementSampleMapper;
import com.shafting.weighing.task.entity.TestRun;
import com.shafting.weighing.task.mapper.TestRunMapper;
import com.shafting.weighing.task.service.MeasurementTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PLC 轮询保存稳定采样及完成顺序测试。 */
class PlcPollingServiceTest {

    @Test
    void shouldSaveFinalSampleBeforeCompletingRunWhenPlcResetsMode() {
        PlcProperties properties = properties();
        DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
        ControlStateService stateService = activeAutomaticState();
        MeasurementSampleMapper sampleMapper = mock(MeasurementSampleMapper.class);
        TestRunMapper runMapper = mock(TestRunMapper.class);
        MeasurementTaskService taskService = mock(MeasurementTaskService.class);
        AlarmService alarmService = mock(AlarmService.class);
        TestRun run = run();
        when(gateway.readFeedback(anyMap())).thenReturn(completedValues());
        when(runMapper.selectById(8L)).thenReturn(run);
        when(sampleMapper.countByRunAndSequence(8L, 9L)).thenReturn(0);

        PlcPollingService service = new PlcPollingService(properties, gateway, stateService,
                sampleMapper, runMapper, taskService, mock(RealtimeStateStore.class),
                mock(RealtimePublisher.class), alarmService);

        service.poll();

        InOrder order = inOrder(sampleMapper, taskService);
        order.verify(sampleMapper).insert(org.mockito.ArgumentMatchers.any(MeasurementSample.class));
        order.verify(taskService).completeFromPlc(8L);
        ArgumentCaptor<MeasurementSample> sampleCaptor = ArgumentCaptor.forClass(MeasurementSample.class);
        verify(sampleMapper).insert(sampleCaptor.capture());
        assertThat(sampleCaptor.getValue().getStage()).isEqualTo("LOWERING");
        assertThat(sampleCaptor.getValue().getForceKn()).isEqualByComparingTo("0.0");
        assertThat(sampleCaptor.getValue().getDisplacementMm()).isEqualByComparingTo("0.00");
        assertThat(stateService.channel("TAIL_SHAFT").action()).isEqualTo(ControlDto.ChannelAction.IDLE);
    }

    private PlcProperties properties() {
        PlcProperties properties = new PlcProperties();
        properties.setMode(PlcProperties.Mode.PLC);
        properties.setFeedbackSelectedChannel("selected");
        properties.setFeedbackMode("mode");
        properties.setFeedbackAction("action");
        properties.setFeedbackRunState("runState");
        properties.setFeedbackProcessStage("stage");
        properties.setFeedbackSampleSequence("sequence");
        properties.setFeedbackFaultCode("fault");
        return properties;
    }

    private ControlStateService activeAutomaticState() {
        ControlStateService stateService = new ControlStateService();
        stateService.connectMode("TAIL_SHAFT", ControlDto.ControlMode.AUTO);
        stateService.markZeroed("TAIL_SHAFT");
        stateService.beginAction("TAIL_SHAFT", ControlDto.ChannelAction.AUTO_TEST, null, true);
        stateService.attachRun("TAIL_SHAFT", 8L);
        return stateService;
    }

    private TestRun run() {
        TestRun run = mock(TestRun.class);
        when(run.getId()).thenReturn(8L);
        when(run.getTaskId()).thenReturn(3L);
        when(run.getPointId()).thenReturn(1L);
        return run;
    }

    private Map<String, BigDecimal> completedValues() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("selectedChannel", BigDecimal.ZERO);
        values.put("mode", BigDecimal.ZERO);
        values.put("action", BigDecimal.ZERO);
        values.put("runState", BigDecimal.valueOf(3));
        values.put("processStage", BigDecimal.valueOf(5));
        values.put("sampleSequence", BigDecimal.valueOf(9));
        values.put("faultCode", BigDecimal.ZERO);
        for (String pointCode : PlcPointCatalog.pointCodes()) {
            values.put(pointCode + ".displacement", BigDecimal.ZERO);
            values.put(pointCode + ".force", BigDecimal.ZERO);
            values.put(pointCode + ".motorRpm", BigDecimal.ZERO);
        }
        for (PlcAlarmCatalog.AlarmTag tag : PlcAlarmCatalog.tags()) {
            values.put("alarm." + tag.code(), BigDecimal.ZERO);
        }
        return values;
    }
}
