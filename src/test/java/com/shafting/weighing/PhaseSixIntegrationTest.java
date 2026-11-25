package com.shafting.weighing;

import com.shafting.weighing.control.service.ControlStateService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Timeout(15)
class PhaseSixIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ControlStateService controlStateService;

    @BeforeEach
    void resetControlAndConfiguration() {
        controlStateService.clearAlarmLock();
        controlStateService.resetControls();
        jdbcTemplate.update("UPDATE alarm_event SET status = 'CLOSED'");
        jdbcTemplate.update("UPDATE test_config_point SET max_displacement = 0.40, "
                + "manual_target_displacement = 0.20, pressure_interval_mpa = 1, hold_seconds = 1");
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void shouldAllowViewerToReadButNotOperateControls() throws Exception {
        mockMvc.perform(get("/api/control/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alarmLocked").value(false))
                .andExpect(jsonPath("$.channels.length()").value(6));

        mockMvc.perform(post("/api/control/commands")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(command("MANUAL_CONNECT", "TAIL_SHAFT", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldControlManualWithoutTaskRunOrSamples() throws Exception {
        int taskCount = count("test_task");
        int runCount = count("test_run");
        int sampleCount = count("measurement_sample");
        execute("MANUAL_CONNECT", "TAIL_SHAFT", null);
        execute("MANUAL_ENABLE", "TAIL_SHAFT", null);
        execute("ZERO_DISPLACEMENT", "TAIL_SHAFT", null);

        JsonNode state = execute("MANUAL_ASCEND", "TAIL_SHAFT", null);

        assertThat(state.at("/channels/0/action").asText()).isEqualTo("ASCENDING");
        assertThat(state.at("/channels/0/manualStarted").asBoolean()).isTrue();
        assertThat(state.at("/channels/0/zeroed").asBoolean()).isFalse();
        assertThat(state.at("/channels/0/targetDisplacementMm").decimalValue()).isEqualByComparingTo("0.20");
        assertThat(state.at("/channels/0/runId").isNull()).isTrue();
        assertThat(count("test_task")).isEqualTo(taskCount);
        assertThat(count("test_run")).isEqualTo(runCount);
        assertThat(count("measurement_sample")).isEqualTo(sampleCount);

        JsonNode paused = execute("MANUAL_PAUSE", "TAIL_SHAFT", null);
        assertThat(paused.at("/channels/0/paused").asBoolean()).isTrue();
        JsonNode resumed = execute("MANUAL_RESUME", "TAIL_SHAFT", null);
        assertThat(resumed.at("/channels/0/paused").asBoolean()).isFalse();
        execute("MANUAL_STOP", "TAIL_SHAFT", null);
        JsonNode restarted = execute("MANUAL_DESCEND", "TAIL_SHAFT", null);
        assertThat(restarted.at("/channels/0/action").asText()).isEqualTo("DESCENDING");
        execute("MANUAL_STOP", "TAIL_SHAFT", null);
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldRejectMissingAndAcceptIndependentManualTarget() throws Exception {
        jdbcTemplate.update("UPDATE test_config_point SET manual_target_displacement = 0 WHERE point_id = 1");
        execute("MANUAL_CONNECT", "TAIL_SHAFT", null);
        execute("MANUAL_ENABLE", "TAIL_SHAFT", null);
        execute("ZERO_DISPLACEMENT", "TAIL_SHAFT", null);
        executeExpectingConflict("MANUAL_ASCEND", "TAIL_SHAFT", null);

        jdbcTemplate.update("UPDATE test_config_point SET manual_target_displacement = 0.41 WHERE point_id = 1");
        JsonNode state = execute("MANUAL_ASCEND", "TAIL_SHAFT", null);
        assertThat(state.at("/channels/0/targetDisplacementMm").decimalValue()).isEqualByComparingTo("0.41");
        execute("MANUAL_STOP", "TAIL_SHAFT", null);
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldPauseResumeStopAndRestartAutomaticActions() throws Exception {
        execute("AUTO_CONNECT", "FLYWHEEL", null);
        execute("ZERO_DISPLACEMENT", "FLYWHEEL", null);
        JsonNode running = execute("AUTO_TEST_START", "FLYWHEEL", null);
        long runId = channel(running, "FLYWHEEL").path("runId").asLong();

        JsonNode paused = execute("AUTO_PAUSE", "FLYWHEEL", null);
        assertThat(channel(paused, "FLYWHEEL").path("paused").asBoolean()).isTrue();
        assertThat(channel(paused, "FLYWHEEL").path("action").asText()).isEqualTo("AUTO_TEST");
        JsonNode resumed = execute("AUTO_RESUME", "FLYWHEEL", null);
        assertThat(channel(resumed, "FLYWHEEL").path("action").asText()).isEqualTo("AUTO_TEST");
        assertThat(channel(resumed, "FLYWHEEL").path("paused").asBoolean()).isFalse();

        execute("AUTO_STOP", "FLYWHEEL", null);
        JsonNode quickRelease = execute("AUTO_QUICK_RELEASE", "FLYWHEEL", null);
        assertThat(quickRelease.path("globalAction").asText()).isEqualTo("AUTO_QUICK_RELEASE");
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM test_run WHERE id = ?", String.class, runId))
                .isEqualTo("STOPPED");
        assertThat(execute("AUTO_PAUSE", "FLYWHEEL", null).path("globalPaused").asBoolean()).isTrue();
        assertThat(execute("AUTO_RESUME", "FLYWHEEL", null).path("globalPaused").asBoolean()).isFalse();
        assertThat(execute("AUTO_STOP", "FLYWHEEL", null).path("globalAction").asText()).isEqualTo("IDLE");
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldRejectAutomaticTestWithoutPressureInterval() throws Exception {
        jdbcTemplate.update("UPDATE test_config_point SET pressure_interval_mpa = 0 WHERE point_id = 4");
        execute("AUTO_CONNECT", "FLYWHEEL", null);
        execute("ZERO_DISPLACEMENT", "FLYWHEEL", null);

        executeExpectingConflict("AUTO_TEST_START", "FLYWHEEL", null);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldStopEverythingAndLockControlsWhenAlarmOccurs() throws Exception {
        execute("MANUAL_CONNECT", "TAIL_SHAFT", null);
        execute("MANUAL_ENABLE", "TAIL_SHAFT", null);
        execute("ZERO_DISPLACEMENT", "TAIL_SHAFT", null);
        int runCount = count("test_run");
        execute("MANUAL_ASCEND", "TAIL_SHAFT", null);

        mockMvc.perform(post("/api/simulator/alarms")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "alarmCode", "PRESSURE_HIGH",
                                "alarmLevel", "HIGH",
                                "sourceName", "尾轴",
                                "message", "压力越限"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/control/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alarmLocked").value(true))
                .andExpect(jsonPath("$.channels[0].mode").value("OFF"));
        assertThat(count("test_run")).isEqualTo(runCount);

        executeExpectingConflict("MANUAL_CONNECT", "TAIL_SHAFT", null);
    }

    private JsonNode execute(String commandType, String pointCode, BigDecimal target) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/control/commands")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(command(commandType, pointCode, target)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void executeExpectingConflict(String commandType, String pointCode, BigDecimal target)
            throws Exception {
        mockMvc.perform(post("/api/control/commands")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(command(commandType, pointCode, target)))
                .andExpect(status().isConflict());
    }

    private byte[] command(String type, String pointCode, BigDecimal target) throws Exception {
        return objectMapper.writeValueAsBytes(Map.of(
                "commandNo", UUID.randomUUID().toString(),
                "commandType", type,
                "pointCode", pointCode == null ? "" : pointCode,
                "targetDisplacementMm", target == null ? BigDecimal.ZERO : target,
                "configId", 1));
    }

    private JsonNode channel(JsonNode state, String pointCode) {
        for (JsonNode item : state.path("channels")) {
            if (pointCode.equals(item.path("pointCode").asText())) {
                return item;
            }
        }
        throw new AssertionError("未找到通道 " + pointCode);
    }

    private int count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }
}
