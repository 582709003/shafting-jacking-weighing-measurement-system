package com.shafting.weighing.control.service;

import com.shafting.weighing.control.dto.ControlDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControlStateServiceTest {

    private ControlStateService service;

    @BeforeEach
    void setUp() {
        service = new ControlStateService();
    }

    @Test
    void shouldRequireManualStartBeforeManualAction() {
        service.connectMode("TAIL_SHAFT", ControlDto.ControlMode.MANUAL);
        service.markZeroed("TAIL_SHAFT");

        assertThatThrownBy(() -> service.beginAction("TAIL_SHAFT", ControlDto.ChannelAction.ASCENDING,
                new BigDecimal("0.20"), true)).isInstanceOf(ResponseStatusException.class);

        service.startManual("TAIL_SHAFT");
        service.beginAction("TAIL_SHAFT", ControlDto.ChannelAction.ASCENDING,
                new BigDecimal("0.20"), true);

        ControlDto.ChannelState channel = service.channel("TAIL_SHAFT");
        assertThat(channel.manualStarted()).isTrue();
        assertThat(channel.action()).isEqualTo(ControlDto.ChannelAction.ASCENDING);
        assertThat(channel.zeroed()).isFalse();
    }

    @Test
    void shouldKeepManualAndAutomaticModesExclusive() {
        service.connectMode("TAIL_SHAFT", ControlDto.ControlMode.MANUAL);
        service.startManual("TAIL_SHAFT");

        service.connectMode("TAIL_SHAFT", ControlDto.ControlMode.AUTO);

        ControlDto.ChannelState channel = service.channel("TAIL_SHAFT");
        assertThat(channel.mode()).isEqualTo(ControlDto.ControlMode.AUTO);
        assertThat(channel.manualStarted()).isFalse();
    }

    @Test
    void shouldPauseResumeAndStopManualActionWithoutDisconnectingManualMode() {
        service.connectMode("TAIL_SHAFT", ControlDto.ControlMode.MANUAL);
        service.startManual("TAIL_SHAFT");
        service.beginAction("TAIL_SHAFT", ControlDto.ChannelAction.DESCENDING, null, false);

        service.pauseChannelAction("TAIL_SHAFT");
        assertThat(service.channel("TAIL_SHAFT").paused()).isTrue();
        assertThat(service.channel("TAIL_SHAFT").action()).isEqualTo(ControlDto.ChannelAction.DESCENDING);

        service.resumeChannelAction("TAIL_SHAFT");
        assertThat(service.channel("TAIL_SHAFT").paused()).isFalse();

        service.finishAction("TAIL_SHAFT", null);
        ControlDto.ChannelState stopped = service.channel("TAIL_SHAFT");
        assertThat(stopped.mode()).isEqualTo(ControlDto.ControlMode.MANUAL);
        assertThat(stopped.manualStarted()).isTrue();
        assertThat(stopped.action()).isEqualTo(ControlDto.ChannelAction.IDLE);
    }

    @Test
    void shouldBlockModeSwitchDuringPausedAction() {
        service.connectMode("TAIL_SHAFT", ControlDto.ControlMode.MANUAL);
        service.startManual("TAIL_SHAFT");
        service.beginAction("TAIL_SHAFT", ControlDto.ChannelAction.DESCENDING, null, false);
        service.pauseChannelAction("TAIL_SHAFT");

        assertThatThrownBy(() -> service.connectMode("TAIL_SHAFT", ControlDto.ControlMode.AUTO))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.connectMode("INTERMEDIATE_1", ControlDto.ControlMode.AUTO))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRequireFreshZeroForEveryAutomaticTest() {
        service.connectMode("TAIL_SHAFT", ControlDto.ControlMode.AUTO);

        assertThatThrownBy(() -> service.beginAction("TAIL_SHAFT", ControlDto.ChannelAction.AUTO_TEST,
                null, true)).isInstanceOf(ResponseStatusException.class);

        service.markZeroed("TAIL_SHAFT");
        service.beginAction("TAIL_SHAFT", ControlDto.ChannelAction.AUTO_TEST, null, true);
        service.finishAction("TAIL_SHAFT", null);

        assertThatThrownBy(() -> service.beginAction("TAIL_SHAFT", ControlDto.ChannelAction.AUTO_TEST,
                null, true)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldPauseResumeAndStopAutomaticQuickRelease() {
        service.connectMode("TAIL_SHAFT", ControlDto.ControlMode.AUTO);
        service.connectAutoQuickRelease("TAIL_SHAFT");

        service.pauseAutoQuickRelease();
        assertThat(service.snapshot().globalPaused()).isTrue();
        assertThatThrownBy(() -> service.disconnectMode("TAIL_SHAFT", ControlDto.ControlMode.AUTO))
                .isInstanceOf(ResponseStatusException.class);

        service.resumeAutoQuickRelease();
        assertThat(service.snapshot().globalPaused()).isFalse();

        service.disconnectAutoQuickRelease();
        assertThat(service.snapshot().globalAction()).isEqualTo(ControlDto.GlobalAction.IDLE);
    }

    @Test
    void shouldLockModesAfterAlarmUntilAllAlarmsAreClosed() {
        service.connectMode("TAIL_SHAFT", ControlDto.ControlMode.MANUAL);
        service.alarmStop();

        assertThat(service.snapshot().alarmLocked()).isTrue();
        assertThat(service.channel("TAIL_SHAFT").mode()).isEqualTo(ControlDto.ControlMode.OFF);
        assertThatThrownBy(() -> service.connectMode("TAIL_SHAFT", ControlDto.ControlMode.MANUAL))
                .isInstanceOf(ResponseStatusException.class);

        service.clearAlarmLock();
        service.connectMode("TAIL_SHAFT", ControlDto.ControlMode.MANUAL);
        assertThat(service.channel("TAIL_SHAFT").mode()).isEqualTo(ControlDto.ControlMode.MANUAL);
    }
}
