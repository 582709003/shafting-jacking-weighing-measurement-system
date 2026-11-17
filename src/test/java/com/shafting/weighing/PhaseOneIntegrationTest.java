package com.shafting.weighing;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseOneIntegrationTest {

    private static final List<String> POINT_CODES = List.of(
            "TAIL_SHAFT", "INTERMEDIATE_1", "INTERMEDIATE_2",
            "FLYWHEEL", "MAIN_ENGINE_1", "MAIN_ENGINE_2");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRejectAnonymousApiRequest() throws Exception {
        mockMvc.perform(get("/api/points"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLoginWithDatabaseAdministrator() throws Exception {
        mockMvc.perform(formLogin().user("admin").password("Admin@123"))
                .andExpect(authenticated().withUsername("admin"));
    }

    @Test
    void shouldRenderLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("登录控制台")))
                .andExpect(content().string(not(containsString("name=\"system\""))))
                .andExpect(content().string(not(containsString("进入系统"))));
    }

    @Test
    void shouldProtectWebConsole() throws Exception {
        mockMvc.perform(get("/prototype/index.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReturnCurrentSessionAndCsrfToken() throws Exception {
        mockMvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.csrfHeaderName").isNotEmpty())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void shouldReturnSixMeasurementPoints() throws Exception {
        mockMvc.perform(get("/api/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].pointCode").value("TAIL_SHAFT"))
                .andExpect(jsonPath("$[5].pointCode").value("MAIN_ENGINE_2"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void shouldReturnDefaultSixPointConfiguration() throws Exception {
        mockMvc.perform(get("/api/configs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configName").value("默认六点参数"))
                .andExpect(jsonPath("$.points.length()").value(6))
                .andExpect(jsonPath("$.points[0].maxPressure").doesNotExist())
                .andExpect(jsonPath("$.points[0].totalValue").isNumber());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void shouldForbidViewerFromUpdatingConfiguration() throws Exception {
        mockMvc.perform(put("/api/configs/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "engineer", roles = "ENGINEER")
    void shouldUpdateAllPointParametersAndWriteAuditLog() throws Exception {
        mockMvc.perform(put("/api/configs/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configName").value("默认六点参数"))
                .andExpect(jsonPath("$.points.length()").value(6))
                .andExpect(jsonPath("$.points[0].forceIntervalKn").value(0.5000));

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE operation = 'UPDATE_CONFIG'",
                Integer.class);
        assertThat(auditCount).isEqualTo(1);
        Integer intervalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_config_point WHERE force_interval_kn = 0.5000",
                Integer.class);
        assertThat(intervalCount).isEqualTo(6);
        String auditJson = jdbcTemplate.queryForObject(
                "SELECT detail_json FROM sys_operation_log WHERE operation = 'UPDATE_CONFIG'",
                String.class);
        assertThat(auditJson).contains("\"forceIntervalKn\":0.5000");
    }

    @Test
    @WithMockUser(username = "engineer", roles = "ENGINEER")
    void shouldRejectIncompletePointConfiguration() throws Exception {
        mockMvc.perform(put("/api/configs/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest(POINT_CODES.subList(0, 5))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "engineer", roles = "ENGINEER")
    void shouldInsertThenUpdateOnlySelectedPointConfiguration() throws Exception {
        jdbcTemplate.update("DELETE FROM test_config_point WHERE config_id = 1 AND point_id = 1");

        mockMvc.perform(put("/api/configs/1/points/TAIL_SHAFT")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pointRequest("TAIL_SHAFT", "125.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].standardForce").value(125.00))
                .andExpect(jsonPath("$.points[0].totalValue").value(700.00));
        mockMvc.perform(put("/api/configs/1/points/TAIL_SHAFT")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pointRequest("TAIL_SHAFT", "138.50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].standardForce").value(138.50));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_config_point WHERE config_id = 1 AND point_id = 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT standard_force FROM test_config_point WHERE config_id = 1 AND point_id = 1",
                BigDecimal.class)).isEqualByComparingTo("138.50");
    }

    @Test
    @WithMockUser(username = "engineer", roles = "ENGINEER")
    void shouldRejectSelectedPointWhenAnyParameterIsMissing() throws Exception {
        mockMvc.perform(put("/api/configs/1/points/TAIL_SHAFT")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "pointCode", "TAIL_SHAFT",
                                "standardForce", new BigDecimal("100.00"),
                                "totalValue", new BigDecimal("700.00"),
                                "jackingCoefficient", new BigDecimal("1.0000"),
                                "maxDisplacement", new BigDecimal("0.40"),
                                "motorRpm", 1200,
                                "forceIntervalKn", new BigDecimal("0.5000")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "engineer", roles = "ENGINEER")
    void shouldInsertThenUpdateManualTargetForSelectedPoint() throws Exception {
        jdbcTemplate.update("DELETE FROM test_config_point WHERE config_id = 1 AND point_id = 1");

        saveManualTarget("0.18");
        saveManualTarget("0.26");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_config_point WHERE config_id = 1 AND point_id = 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT manual_target_displacement FROM test_config_point WHERE config_id = 1 AND point_id = 1",
                BigDecimal.class)).isEqualByComparingTo("0.26");
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldForbidOperatorFromUpdatingManualTarget() throws Exception {
        mockMvc.perform(put("/api/configs/1/points/TAIL_SHAFT/manual-target")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "targetDisplacementMm", new BigDecimal("0.26")))))
                .andExpect(status().isForbidden());
    }

    private byte[] updateRequest() throws Exception {
        return updateRequest(POINT_CODES);
    }

    private byte[] updateRequest(List<String> pointCodes) throws Exception {
        List<Map<String, Object>> points = pointCodes.stream()
                .map(code -> Map.<String, Object>of(
                        "pointCode", code,
                        "standardForce", new BigDecimal("100.00"),
                        "totalValue", new BigDecimal("600.00"),
                        "jackingCoefficient", new BigDecimal("1.0000"),
                        "maxDisplacement", new BigDecimal("0.40"),
                        "motorRpm", 1200,
                        "forceIntervalKn", new BigDecimal("0.5000"),
                        "holdSeconds", 5))
                .toList();
        return objectMapper.writeValueAsBytes(Map.of(
                "configName", "默认六点参数",
                "points", points));
    }

    private byte[] pointRequest(String pointCode, String standardForce) throws Exception {
        return objectMapper.writeValueAsBytes(Map.of(
                "pointCode", pointCode,
                "standardForce", new BigDecimal(standardForce),
                "totalValue", new BigDecimal("700.00"),
                "jackingCoefficient", new BigDecimal("1.0000"),
                "maxDisplacement", new BigDecimal("0.40"),
                "motorRpm", 1200,
                "forceIntervalKn", new BigDecimal("0.5000"),
                "holdSeconds", 5));
    }

    private void saveManualTarget(String target) throws Exception {
        mockMvc.perform(put("/api/configs/1/points/TAIL_SHAFT/manual-target")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "targetDisplacementMm", new BigDecimal(target)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].manualTargetDisplacement").value(Double.parseDouble(target)));
    }
}
