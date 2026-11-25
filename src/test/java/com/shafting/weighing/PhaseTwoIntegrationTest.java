package com.shafting.weighing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseTwoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void configureExecutionLimits() {
        jdbcTemplate.update("UPDATE test_config_point SET max_displacement = 0.40, pressure_interval_mpa = 25");
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void shouldForbidViewerFromCreatingTask() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTaskBody("TAIL_SHAFT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldCreateTaskAndRejectInvalidCommand() throws Exception {
        long runId = createTask("INTERMEDIATE_1").path("runId").asLong();

        mockMvc.perform(post("/api/runs/{runId}/commands", runId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody("invalid-pause", "PAUSE")))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldRunIdempotentCommandLifecycle() throws Exception {
        JsonNode task = createTask("TAIL_SHAFT");
        long runId = task.path("runId").asLong();
        String startCommandNo = UUID.randomUUID().toString();

        sendCommand(runId, startCommandNo, "START", "RUNNING");
        sendCommand(runId, startCommandNo, "START", "RUNNING");
        sendCommand(runId, UUID.randomUUID().toString(), "PAUSE", "PAUSED");
        sendCommand(runId, UUID.randomUUID().toString(), "RESUME", "RUNNING");
        sendCommand(runId, UUID.randomUUID().toString(), "STOP", "STOPPED");

        Integer commandCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM device_command WHERE run_id = ?", Integer.class, runId);
        assertThat(commandCount).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldAllowOnlyOneActiveRun() throws Exception {
        long firstRunId = createTask("INTERMEDIATE_2").path("runId").asLong();
        sendCommand(firstRunId, UUID.randomUUID().toString(), "START", "RUNNING");
        long secondRunId = createTask("FLYWHEEL").path("runId").asLong();

        mockMvc.perform(post("/api/runs/{runId}/commands", secondRunId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(UUID.randomUUID().toString(), "START")))
                .andExpect(status().isConflict());

        sendCommand(firstRunId, UUID.randomUUID().toString(), "STOP", "STOPPED");
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldOpenServerSentEventStream() throws Exception {
        long runId = createTask("MAIN_ENGINE_1").path("runId").asLong();
        mockMvc.perform(get("/api/runs/{runId}/events", runId))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldOpenPointRealtimeStreamForManualControl() throws Exception {
        mockMvc.perform(get("/api/points/{pointCode}/events", "TAIL_SHAFT"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    private JsonNode createTask(String pointCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTaskBody(pointCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("IDLE"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void sendCommand(long runId, String commandNo, String commandType, String expectedState)
            throws Exception {
        mockMvc.perform(post("/api/runs/{runId}/commands", runId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(commandNo, commandType)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(expectedState));
    }

    private byte[] createTaskBody(String pointCode) throws Exception {
        return objectMapper.writeValueAsBytes(Map.of(
                "taskName", "集成测试任务",
                "configId", 1,
                "pointCode", pointCode));
    }

    private byte[] commandBody(String commandNo, String commandType) throws Exception {
        return objectMapper.writeValueAsBytes(Map.of(
                "commandNo", commandNo,
                "commandType", commandType));
    }
}
