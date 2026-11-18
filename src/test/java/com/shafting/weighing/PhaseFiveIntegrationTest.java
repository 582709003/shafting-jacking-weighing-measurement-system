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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseFiveIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldListRecordsAndExportUtf8Csv() throws Exception {
        createRun();
        mockMvc.perform(get("/api/records")
                        .param("page", "1")
                        .param("size", "1")
                        .param("keyword", "记录测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records.length()").value(1))
                .andExpect(jsonPath("$.records[0].taskNo").isNotEmpty())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
        mockMvc.perform(get("/api/records/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("任务编号")));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void shouldForbidViewerFromChangingAlarmState() throws Exception {
        long alarmId = insertAlarm();
        mockMvc.perform(post("/api/alarms/{id}/acknowledge", alarmId).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldAcknowledgeRecoverAndCloseAlarmWithActionHistory() throws Exception {
        long alarmId = insertAlarm();
        action(alarmId, "acknowledge", "ACKNOWLEDGED");
        action(alarmId, "recover", "RECOVERED");
        action(alarmId, "close", "CLOSED");
        mockMvc.perform(get("/api/alarms/{id}", alarmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.length()").value(3));
    }

    private long createRun() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
                "taskName", "记录测试", "configId", 1, "pointCode", "MAIN_ENGINE_2"));
        String response = mockMvc.perform(post("/api/tasks").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("runId").asLong();
    }

    private long insertAlarm() {
        jdbcTemplate.update("""
                INSERT INTO alarm_event
                    (alarm_code, alarm_level, source_name, message, status, occurred_at)
                VALUES ('SIM_CONNECTION', 'WARNING', '设备模拟器', '模拟连接中断', 'ACTIVE', CURRENT_TIMESTAMP)
                """);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM alarm_event", Long.class);
    }

    private void action(long alarmId, String action, String expectedStatus) throws Exception {
        mockMvc.perform(post("/api/alarms/{id}/{action}", alarmId, action).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"remark\":\"集成测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }
}
