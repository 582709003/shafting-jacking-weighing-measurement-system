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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseThreeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldReturnPersistedRawSamplesInSequence() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
                "taskName", "采样查询测试",
                "configId", 1,
                "pointCode", "TAIL_SHAFT"));
        String response = mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode task = objectMapper.readTree(response);
        long runId = task.path("runId").asLong();
        Long pointId = jdbcTemplate.queryForObject(
                "SELECT point_id FROM test_run WHERE id = ?", Long.class, runId);

        jdbcTemplate.update("""
                INSERT INTO measurement_sample
                    (run_id, point_id, stage, sequence_no, captured_at,
                     force_kn, displacement_mm, raw_payload_json, valid)
                VALUES (?, ?, 'LIFTING', 1, CURRENT_TIMESTAMP, 50.0, 0.20, '{}', 1)
                """, runId, pointId);
        jdbcTemplate.update("""
                INSERT INTO measurement_result
                    (run_id, standard_force_kn, measured_force_kn, deviation_kn,
                     adjustment_coefficient, revision_no, result_status)
                VALUES (?, 0, 0, 0, 1, 1, 'SAVED')
                """, runId);
        jdbcTemplate.update("UPDATE test_run SET state='COMPLETED', process_stage='COMPLETED' WHERE id=?", runId);

        mockMvc.perform(get("/api/runs/{runId}/samples", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sequenceNo").value(1))
                .andExpect(jsonPath("$[0].stage").value("LIFTING"))
                .andExpect(jsonPath("$[0].displacementMm").value(0.20));
    }
}
