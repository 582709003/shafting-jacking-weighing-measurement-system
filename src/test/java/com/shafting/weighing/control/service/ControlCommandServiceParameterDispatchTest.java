package com.shafting.weighing.control.service;

import com.shafting.weighing.alarm.mapper.AlarmEventMapper;
import com.shafting.weighing.control.dto.ControlDto;
import com.shafting.weighing.device.config.PlcProperties;
import com.shafting.weighing.device.service.DeviceCommandGateway;
import com.shafting.weighing.device.service.DeviceSimulator;
import com.shafting.weighing.measurement.entity.MeasurementPoint;
import com.shafting.weighing.measurement.entity.TestConfigPoint;
import com.shafting.weighing.measurement.mapper.MeasurementPointMapper;
import com.shafting.weighing.measurement.mapper.OperationLogMapper;
import com.shafting.weighing.measurement.mapper.TestConfigPointMapper;
import com.shafting.weighing.security.mapper.SysUserMapper;
import com.shafting.weighing.task.dto.TaskDto;
import com.shafting.weighing.task.service.MeasurementTaskService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 控制命令触发参数下发时机测试。 */
class ControlCommandServiceParameterDispatchTest {

    private final ControlStateService stateService = new ControlStateService();
    private final MeasurementTaskService taskService = mock(MeasurementTaskService.class);
    private final DeviceCommandGateway deviceGateway = mock(DeviceCommandGateway.class);
    private final MeasurementPointMapper pointMapper = mock(MeasurementPointMapper.class);
    private final TestConfigPointMapper configPointMapper = mock(TestConfigPointMapper.class);
    private ControlCommandService service;

    @BeforeEach
    void setUp() {
        PlcProperties plcProperties = new PlcProperties();
        plcProperties.setMode(PlcProperties.Mode.PLC);
        MeasurementPoint point = mock(MeasurementPoint.class);
        when(point.getId()).thenReturn(1L);
        when(point.getPointCode()).thenReturn("TAIL_SHAFT");
        when(point.getPointName()).thenReturn("尾轴");
        when(point.getEnabled()).thenReturn(1);
        when(pointMapper.selectOne(any())).thenReturn(point);
        when(configPointMapper.selectOne(any())).thenReturn(configuration());
        AlarmEventMapper alarmEventMapper = mock(AlarmEventMapper.class);
        when(alarmEventMapper.countOpenAlarms()).thenReturn(0);
        service = new ControlCommandService(stateService, taskService, mock(DeviceSimulator.class),
                deviceGateway, plcProperties, pointMapper, configPointMapper, alarmEventMapper,
                mock(OperationLogMapper.class), mock(SysUserMapper.class), new ObjectMapper());
    }

    @Test
    void shouldWriteManualTargetOnlyWhenAscending() {
        service.execute(command(ControlDto.CommandType.MANUAL_CONNECT), "operator");
        service.execute(command(ControlDto.CommandType.MANUAL_ENABLE), "operator");
        service.execute(command(ControlDto.CommandType.ZERO_DISPLACEMENT), "operator");
        verify(deviceGateway, never()).writeManualTarget(anyString(), any());

        service.execute(command(ControlDto.CommandType.MANUAL_ASCEND), "operator");

        InOrder order = inOrder(deviceGateway);
        order.verify(deviceGateway).writeManualTarget("TAIL_SHAFT", new BigDecimal("0.20"));
        order.verify(deviceGateway).sendAscending("TAIL_SHAFT");
        verify(deviceGateway, never()).writeAutomaticConfiguration(anyString(), any());
    }

    @Test
    void shouldWriteAutomaticControlParametersBeforeStartingTask() {
        TaskDto.TaskResponse idle = task("IDLE");
        TaskDto.TaskResponse running = task("RUNNING");
        when(taskService.createAutomaticControlTask(any(), anyString(), anyString(), anyString()))
                .thenReturn(idle);
        when(taskService.command(any(), any())).thenReturn(running);
        service.execute(command(ControlDto.CommandType.AUTO_CONNECT), "operator");
        service.execute(command(ControlDto.CommandType.ZERO_DISPLACEMENT), "operator");

        service.execute(command(ControlDto.CommandType.AUTO_TEST_START), "operator");

        InOrder order = inOrder(deviceGateway, taskService);
        ArgumentCaptor<DeviceCommandGateway.AutomaticConfiguration> values =
                ArgumentCaptor.forClass(DeviceCommandGateway.AutomaticConfiguration.class);
        order.verify(deviceGateway).writeAutomaticConfiguration(anyString(), values.capture());
        order.verify(taskService).createAutomaticControlTask(any(), anyString(), anyString(), anyString());
        order.verify(taskService).command(any(), any());
        assertThat(values.getValue()).isEqualTo(new DeviceCommandGateway.AutomaticConfiguration(
                new BigDecimal("0.40"), new BigDecimal("1200"), 20, new BigDecimal("5")));
        verify(deviceGateway, never()).writeManualTarget(anyString(), any());
    }

    private ControlDto.CommandRequest command(ControlDto.CommandType type) {
        return new ControlDto.CommandRequest(type.name(), type, "TAIL_SHAFT", null, 1L);
    }

    private TaskDto.TaskResponse task(String state) {
        return new TaskDto.TaskResponse(1L, 2L, "TASK-1", "尾轴自动测试", "TAIL_SHAFT", "尾轴",
                "AUTOMATIC", state, "WAITING");
    }

    private TestConfigPoint configuration() {
        TestConfigPoint config = new TestConfigPoint();
        config.setManualTargetDisplacement(new BigDecimal("0.20"));
        config.setStandardForce(new BigDecimal("100"));
        config.setJackingCoefficient(BigDecimal.ONE);
        config.setMaxDisplacement(new BigDecimal("0.40"));
        config.setMotorRpm(1200);
        config.setPressureIntervalMpa(new BigDecimal("20"));
        config.setHoldSeconds(5);
        return config;
    }
}
