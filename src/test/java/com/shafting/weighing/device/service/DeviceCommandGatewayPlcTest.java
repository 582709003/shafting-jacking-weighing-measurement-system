package com.shafting.weighing.device.service;

import com.shafting.weighing.control.dto.ControlDto;
import com.shafting.weighing.device.config.PlcProperties;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PLC 模式命令顺序测试。 */
class DeviceCommandGatewayPlcTest {

    @Test
    void shouldSelectChannelBeforeConnectingManualMode() {
        PlcProperties properties = plcProperties();
        S7PlcClient client = mock(S7PlcClient.class);
        when(client.readValues(anyMap())).thenReturn(configurationReadBack(248));
        DeviceCommandGateway gateway = new DeviceCommandGateway(properties, client);

        gateway.connectMode("INTERMEDIATE_1", ControlDto.ControlMode.MANUAL, pointConfiguration());

        InOrder order = inOrder(client);
        order.verify(client).pulse("%DB100.DBX2.2:BOOL");
        order.verify(client).awaitInt("%DB101.DBW0:INT", 3, properties.getFeedbackTimeoutMs());
        order.verify(client).writeValues(anyMap());
        order.verify(client).readValues(anyMap());
        order.verify(client).pulse("%DB100.DBX0.0:BOOL");
        order.verify(client).awaitInt("%DB101.DBW2:INT", 1, properties.getFeedbackTimeoutMs());
    }

    @Test
    void shouldDisconnectModeWithoutSelectingChannelAgain() {
        PlcProperties properties = plcProperties();
        S7PlcClient client = mock(S7PlcClient.class);
        DeviceCommandGateway gateway = new DeviceCommandGateway(properties, client);

        gateway.disconnectMode(ControlDto.ControlMode.AUTO);

        InOrder order = inOrder(client);
        order.verify(client).pulse("%DB100.DBX0.1:BOOL");
        order.verify(client).awaitInt("%DB101.DBW2:INT", 0, properties.getFeedbackTimeoutMs());
    }

    @Test
    void shouldNotSendModeCommandWhenChannelConfirmationTimesOut() {
        PlcProperties properties = plcProperties();
        S7PlcClient client = mock(S7PlcClient.class);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,
                "PLC 通道确认超时"))
                .when(client).awaitInt("%DB101.DBW0:INT", 3, properties.getFeedbackTimeoutMs());
        DeviceCommandGateway gateway = new DeviceCommandGateway(properties, client);

        assertThatThrownBy(() -> gateway.connectMode(
                "INTERMEDIATE_1", ControlDto.ControlMode.MANUAL, pointConfiguration()))
                .isInstanceOf(ResponseStatusException.class);

        verify(client, never()).pulse("%DB100.DBX0.0:BOOL");
    }

    @Test
    void shouldNotSendModeCommandWhenConfigurationReadBackDiffers() {
        PlcProperties properties = plcProperties();
        S7PlcClient client = mock(S7PlcClient.class);
        when(client.readValues(anyMap())).thenReturn(Map.of(
                "%DB100.DBD248:REAL", new BigDecimal("99"),
                "%DB100.DBD252:REAL", BigDecimal.ONE,
                "%DB100.DBD256:REAL", new BigDecimal("0.40"),
                "%DB100.DBD260:REAL", new BigDecimal("1200"),
                "%DB100.DBW264:INT", new BigDecimal("20"),
                "%DB100.DBD266:REAL", new BigDecimal("5")));
        DeviceCommandGateway gateway = new DeviceCommandGateway(properties, client);

        assertThatThrownBy(() -> gateway.connectMode(
                "INTERMEDIATE_1", ControlDto.ControlMode.MANUAL, pointConfiguration()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PLC 参数回读不一致");

        verify(client, never()).pulse("%DB100.DBX0.0:BOOL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWriteAndReadBackOnlyPlcConfigurationFields() {
        PlcProperties properties = plcProperties();
        S7PlcClient client = mock(S7PlcClient.class);
        Map<String, BigDecimal> readBack = Map.of(
                "%DB100.DBD196:REAL", new BigDecimal("100"),
                "%DB100.DBD200:REAL", BigDecimal.ONE,
                "%DB100.DBD204:REAL", new BigDecimal("0.40"),
                "%DB100.DBD208:REAL", new BigDecimal("1200"),
                "%DB100.DBW212:INT", new BigDecimal("20"),
                "%DB100.DBD214:REAL", new BigDecimal("5"));
        when(client.readValues(anyMap())).thenReturn(readBack);
        DeviceCommandGateway gateway = new DeviceCommandGateway(properties, client);

        gateway.writePointConfiguration("TAIL_SHAFT", new DeviceCommandGateway.PointConfiguration(
                new BigDecimal("100"), BigDecimal.ONE, new BigDecimal("0.40"),
                new BigDecimal("1200"), 20, new BigDecimal("5")));

        ArgumentCaptor<Map<String, Object>> writes = ArgumentCaptor.forClass(Map.class);
        verify(client).writeValues(writes.capture());
        assertThat(writes.getValue()).hasSize(6)
                .containsEntry("%DB100.DBW212:INT", 20)
                .doesNotContainKey("totalValue");
    }

    private PlcProperties plcProperties() {
        PlcProperties properties = new PlcProperties();
        properties.setMode(PlcProperties.Mode.PLC);
        properties.setFeedbackSelectedChannel("%DB101.DBW0:INT");
        properties.setFeedbackMode("%DB101.DBW2:INT");
        return properties;
    }

    private DeviceCommandGateway.PointConfiguration pointConfiguration() {
        return new DeviceCommandGateway.PointConfiguration(
                new BigDecimal("100"), BigDecimal.ONE, new BigDecimal("0.40"),
                new BigDecimal("1200"), 20, new BigDecimal("5"));
    }

    private Map<String, BigDecimal> configurationReadBack(int startOffset) {
        return Map.of(
                "%DB100.DBD" + startOffset + ":REAL", new BigDecimal("100"),
                "%DB100.DBD" + (startOffset + 4) + ":REAL", BigDecimal.ONE,
                "%DB100.DBD" + (startOffset + 8) + ":REAL", new BigDecimal("0.40"),
                "%DB100.DBD" + (startOffset + 12) + ":REAL", new BigDecimal("1200"),
                "%DB100.DBW" + (startOffset + 16) + ":INT", new BigDecimal("20"),
                "%DB100.DBD" + (startOffset + 18) + ":REAL", new BigDecimal("5"));
    }
}
