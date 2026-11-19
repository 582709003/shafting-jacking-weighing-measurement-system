package com.shafting.weighing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shafting.weighing.alarm.service.AlarmService;
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
    @Autowired private AlarmService alarmService;

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

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldPageAndFilterAlarmStatusGroups() throws Exception {
        String marker = "分页筛选-" + System.nanoTime();
        insertAlarm("ACTIVE", "ACTIVE_" + marker, "CRITICAL", "尾轴", marker + "活动报警");
        insertAlarm("ACKNOWLEDGED", "ACK_" + marker, "WARNING", "中轴1", marker + "处理中报警");
        insertAlarm("RECOVERED", "RECOVERED_" + marker, "WARNING", "中轴2", marker + "已恢复待关闭");
        insertAlarm("CLOSED", "CLOSED_" + marker, "CRITICAL", "飞轮", marker + "已关闭报警");

        mockMvc.perform(get("/api/alarms")
                        .param("statusGroup", "PROCESSING")
                        .param("keyword", marker)
                        .param("alarmLevel", "WARNING")
                        .param("startTime", "2020-01-01T00:00:00")
                        .param("endTime", "2030-01-01T00:00:00")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].status",
                        org.hamcrest.Matchers.isOneOf("ACKNOWLEDGED", "RECOVERED")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.activeCount").isNumber())
                .andExpect(jsonPath("$.processingCount").isNumber())
                .andExpect(jsonPath("$.closedCount").isNumber());
    }

    @Test
    void shouldDeduplicateOpenPlcAlarmAndAllowNewEventAfterClose() {
        String code = "PLC_DUPLICATE_" + System.nanoTime();
        alarmService.activateFromPlc(code, "S7-1200", "PLC去重测试");
        alarmService.activateFromPlc(code, "S7-1200", "PLC去重测试");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alarm_event WHERE alarm_code = ?", Integer.class, code)).isEqualTo(1);

        Long alarmId = jdbcTemplate.queryForObject(
                "SELECT id FROM alarm_event WHERE alarm_code = ?", Long.class, code);
        alarmService.action(alarmId, "acknowledge", "测试确认", "admin");
        alarmService.action(alarmId, "recover", "测试恢复", "admin");
        alarmService.action(alarmId, "close", "测试关闭", "admin");
        alarmService.activateFromPlc(code, "S7-1200", "PLC再次报警");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alarm_event WHERE alarm_code = ?", Integer.class, code)).isEqualTo(2);
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

    private void insertAlarm(String status, String code, String level, String source, String message) {
        jdbcTemplate.update("""
                INSERT INTO alarm_event
                    (alarm_code, alarm_level, source_name, message, status, occurred_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, code, level, source, message, status);
    }

    private void action(long alarmId, String action, String expectedStatus) throws Exception {
        mockMvc.perform(post("/api/alarms/{id}/{action}", alarmId, action).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"remark\":\"集成测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }
}
