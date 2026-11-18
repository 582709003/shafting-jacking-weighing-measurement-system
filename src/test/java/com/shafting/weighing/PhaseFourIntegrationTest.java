package com.shafting.weighing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseFourIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldAutoFitAdjacentSamplesAndKeepAdjustmentAmountEmpty() throws Exception {
        long runId = createRun();
        insertSamples(runId);

        mockMvc.perform(post("/api/runs/{runId}/curve-fit/auto", runId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("AUTO"))
                .andExpect(jsonPath("$.lifting.first.displacementMm").value(0))
                .andExpect(jsonPath("$.lifting.second.displacementMm").value(0.15))
                .andExpect(jsonPath("$.measuredForceKn").isNumber())
                .andExpect(jsonPath("$.adjustmentAmountMm").doesNotExist());
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void shouldForbidViewerFromManualCurveRevision() throws Exception {
        mockMvc.perform(post("/api/runs/1/curve-fit/manual")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(manualBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldCreateManualRevisionWithoutChangingRawSamples() throws Exception {
        long runId = createRun();
        insertSamples(runId);
        Integer before = sampleCount(runId);

        mockMvc.perform(post("/api/runs/{runId}/curve-fit/manual", runId)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(manualBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.revisionNo").value(1))
                .andExpect(jsonPath("$.lifting.second.displacementMm").value(0.12))
                .andExpect(jsonPath("$.lowering.second.displacementMm").value(0.13));

        assertThat(sampleCount(runId)).isEqualTo(before);
        mockMvc.perform(get("/api/runs/{runId}/curve-fit", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.lifting.first.forceKn").value(30));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldRejectManualRevisionWhenLowerEndpointLeavesForceAxis() throws Exception {
        long runId = createRun();
        insertSamples(runId);
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
                "lifting", Map.of("first", Map.of("forceKn", 30, "displacementMm", 0.01),
                        "second", Map.of("forceKn", 60, "displacementMm", 0.12)),
                "lowering", Map.of("first", Map.of("forceKn", 25, "displacementMm", 0),
                        "second", Map.of("forceKn", 55, "displacementMm", 0.13))));

        mockMvc.perform(post("/api/runs/{runId}/curve-fit/manual", runId)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private long createRun() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
                "taskName", "拟合测试", "configId", 1, "pointCode", "TAIL_SHAFT"));
        String response = mockMvc.perform(post("/api/tasks").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("runId").asLong();
    }

    private void insertSamples(long runId) {
        Long pointId = jdbcTemplate.queryForObject("SELECT point_id FROM test_run WHERE id=?", Long.class, runId);
        insert(runId, pointId, "LIFTING", 1, "20", "0.02");
        insert(runId, pointId, "LIFTING", 2, "40", "0.05");
        insert(runId, pointId, "LIFTING", 3, "60", "0.15");
        insert(runId, pointId, "LOWERING", 4, "55", "0.15");
        insert(runId, pointId, "LOWERING", 5, "35", "0.05");
        insert(runId, pointId, "LOWERING", 6, "15", "0.02");
    }

    private void insert(long runId, long pointId, String stage, int sequence, String force, String displacement) {
        jdbcTemplate.update("""
                INSERT INTO measurement_sample
                    (run_id, point_id, stage, sequence_no, captured_at,
                     force_kn, displacement_mm, raw_payload_json, valid)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, '{}', 1)
                """, runId, pointId, stage, sequence, force, displacement);
    }

    private Integer sampleCount(long runId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM measurement_sample WHERE run_id=?", Integer.class, runId);
    }

    private byte[] manualBody() throws Exception {
        return objectMapper.writeValueAsBytes(Map.of(
                "lifting", Map.of("first", Map.of("forceKn", 30, "displacementMm", 0),
                        "second", Map.of("forceKn", 60, "displacementMm", 0.12)),
                "lowering", Map.of("first", Map.of("forceKn", 25, "displacementMm", 0),
                        "second", Map.of("forceKn", 55, "displacementMm", 0.13))));
    }
}
