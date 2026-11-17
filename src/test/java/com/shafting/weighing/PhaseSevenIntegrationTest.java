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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseSevenIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldExposeCurveSamplesAfterTheWholeRunCompletes() throws Exception {
        long runId = createResult("INTERMEDIATE_1");

        mockMvc.perform(get("/api/results/latest").param("pointCode", "INTERMEDIATE_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointCode").value("INTERMEDIATE_1"))
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.resultStatus").value("DRAFT"))
                .andExpect(jsonPath("$.result.measuredForceKn").isNumber())
                .andExpect(jsonPath("$.result.adjustmentCoefficient").doesNotExist());

        mockMvc.perform(get("/api/runs/{runId}/result", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.pointCode").value("INTERMEDIATE_1"));

        mockMvc.perform(get("/api/runs/{runId}/samples", runId))
                .andExpect(status().isConflict());

        jdbcTemplate.update("UPDATE test_run SET state='COMPLETED', process_stage='COMPLETED' WHERE id=?", runId);
        jdbcTemplate.update("""
                UPDATE test_task SET task_status='COMPLETED', completed_at=CURRENT_TIMESTAMP
                WHERE id=(SELECT task_id FROM test_run WHERE id=?)
                """, runId);

        mockMvc.perform(get("/api/runs/{runId}/samples", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pressureMpa").doesNotExist())
                .andExpect(jsonPath("$[0].forceKn").value(20.0))
                .andExpect(jsonPath("$[0].displacementMm").value(0.02));

        Integer pressureColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'measurement_sample'
                  AND LOWER(COLUMN_NAME) = 'pressure_mpa'
                """, Integer.class);
        assertThat(pressureColumnCount).isZero();

        mockMvc.perform(get("/api/records/completed").param("pointCode", "INTERMEDIATE_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").value(runId))
                .andExpect(jsonPath("$[0].pointCode").value("INTERMEDIATE_1"));

        mockMvc.perform(post("/api/runs/{runId}/result/save", runId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultStatus").value("SAVED"))
                .andExpect(jsonPath("$.savedAt").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldLogicallyDeleteResultAndRawSamples() throws Exception {
        long runId = createResult("MAIN_ENGINE_2");
        mockMvc.perform(post("/api/runs/{runId}/result/save", runId).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/runs/{runId}/result", runId).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM measurement_result WHERE run_id = ?", Integer.class, runId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM measurement_sample WHERE run_id = ? AND valid = 1",
                Integer.class, runId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM curve_fit_revision WHERE run_id = ?", Integer.class, runId)).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE module = 'RESULT' AND operation = 'DELETE'",
                Integer.class)).isPositive();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldAllowAdminToViewAndUpdateAdjustmentCoefficient() throws Exception {
        long runId = createResult("TAIL_SHAFT");
        mockMvc.perform(post("/api/runs/{runId}/result/save", runId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.adjustmentCoefficient").isNumber());

        mockMvc.perform(patch("/api/runs/{runId}/result/adjustment-coefficient", runId)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adjustmentCoefficient\":1.2500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultStatus").value("DRAFT"))
                .andExpect(jsonPath("$.result.adjustmentCoefficient").value(1.2500))
                .andExpect(jsonPath("$.result.measuredForceKn").isNumber())
                .andExpect(jsonPath("$.result.deviationKn").isNumber());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT adjustment_coefficient FROM measurement_result WHERE run_id = ?",
                java.math.BigDecimal.class, runId)).isEqualByComparingTo("1.2500");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_operation_log
                WHERE module = 'RESULT' AND operation = 'UPDATE_ADJUSTMENT_COEFFICIENT'
                """, Integer.class)).isPositive();
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void shouldForbidViewerFromSavingOrDeletingResult() throws Exception {
        long runId = 1L;

        mockMvc.perform(post("/api/runs/{runId}/result/save", runId).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/runs/{runId}/result", runId).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/runs/{runId}/result/adjustment-coefficient", runId)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adjustmentCoefficient\":1.2500}"))
                .andExpect(status().isForbidden());
    }

    private long createResult(String pointCode) throws Exception {
        long runId = createResultDirectly(pointCode);
        mockMvc.perform(post("/api/runs/{runId}/curve-fit/auto", runId).with(csrf()))
                .andExpect(status().isOk());
        return runId;
    }

    private long createResultDirectly(String pointCode) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
                "taskName", "数值页保存测试", "configId", 1, "pointCode", pointCode));
        JsonNode task = objectMapper.readTree(mockMvc.perform(post("/api/tasks").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        long runId = task.path("runId").asLong();
        Long pointId = jdbcTemplate.queryForObject(
                "SELECT point_id FROM test_run WHERE id = ?", Long.class, runId);
        insertSample(runId, pointId, "LIFTING", 1, "20.0", "0.02");
        insertSample(runId, pointId, "LIFTING", 2, "40.0", "0.08");
        insertSample(runId, pointId, "LOWERING", 3, "35.0", "0.08");
        insertSample(runId, pointId, "LOWERING", 4, "15.0", "0.02");
        return runId;
    }

    private void insertSample(long runId, long pointId, String stage, int sequence,
                              String force, String displacement) {
        jdbcTemplate.update("""
                INSERT INTO measurement_sample
                    (run_id, point_id, stage, sequence_no, captured_at,
                     force_kn, displacement_mm, raw_payload_json, valid)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, '{}', 1)
                """, runId, pointId, stage, sequence, force, displacement);
    }
}
