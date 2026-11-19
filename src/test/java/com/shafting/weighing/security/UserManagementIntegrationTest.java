package com.shafting.weighing.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void adminShouldCreateAndListMultiRoleUserWithoutAuditingPassword() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of(
                                "username", "  New.User  ",
                                "displayName", "新用户",
                                "password", "Password123",
                                "roles", List.of("ENGINEER", "VIEWER")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new.user"))
                .andExpect(jsonPath("$.displayName").value("新用户"))
                .andExpect(jsonPath("$.roles[0]").value("ENGINEER"))
                .andExpect(jsonPath("$.roles[1]").value("VIEWER"));

        Long userId = userId("new.user");
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE id = ?", String.class, userId);
        assertThat(passwordEncoder.matches("Password123", passwordHash)).isTrue();

        mockMvc.perform(get("/api/users/roles").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        mockMvc.perform(get("/api/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'new.user')].roles.length()").value(2));

        String audit = jdbcTemplate.queryForObject(
                "SELECT detail_json FROM sys_operation_log WHERE operation = 'CREATE_USER' AND business_id = ?",
                String.class, userId);
        assertThat(audit).contains("new.user", "ENGINEER", "VIEWER")
                .doesNotContain("Password123", "password");
        mockMvc.perform(formLogin().user("NEW.USER").password("Password123"))
                .andExpect(authenticated().withUsername("new.user"));
    }

    @Test
    void nonAdminShouldNotManageUsers() throws Exception {
        Long targetId = insertUser("managed-user", "Managed123", List.of("VIEWER"));
        var operator = user("operator").roles("OPERATOR");

        mockMvc.perform(get("/api/users").with(operator)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users").with(operator).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("username", "blocked", "displayName", "无权限",
                                "password", "Password123", "roles", List.of("VIEWER")))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/{id}/roles", targetId).with(operator).with(csrf())
                        .contentType("application/json").content(json(Map.of("roles", List.of("ENGINEER")))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/{id}/password", targetId).with(operator).with(csrf())
                        .contentType("application/json").content(json(Map.of("newPassword", "NewPassword"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldValidateCreateRequestAndRejectDuplicateUsername() throws Exception {
        insertUser("duplicate", "Password123", List.of("VIEWER"));
        var admin = user("admin").roles("ADMIN");

        mockMvc.perform(post("/api/users").with(admin).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("username", "DUPLICATE", "displayName", "重复",
                                "password", "Password123", "roles", List.of("VIEWER")))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/users").with(admin).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("username", "bad user", "displayName", "错误",
                                "password", "short", "roles", List.of()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users").with(admin).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("username", "unknown-role", "displayName", "未知角色",
                                "password", "Password123", "roles", List.of("UNKNOWN")))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users").with(admin).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("username", "duplicate-role", "displayName", "重复角色",
                                "password", "Password123", "roles", List.of("VIEWER", "viewer")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminShouldUpdateRolesButNotOwnRolesOrLastAdministrator() throws Exception {
        Long targetId = insertUser("role-target", "Password123", List.of("OPERATOR"));
        Long adminId = userId("admin");
        var admin = user("admin").roles("ADMIN");

        mockMvc.perform(put("/api/users/{id}/roles", targetId).with(admin).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("roles", List.of("ENGINEER", "VIEWER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ENGINEER"))
                .andExpect(jsonPath("$.roles[1]").value("VIEWER"));

        mockMvc.perform(put("/api/users/{id}/roles", adminId).with(admin).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("roles", List.of("VIEWER")))))
                .andExpect(status().isConflict());

        Long staleAdminId = insertUser("stale-admin", "Password123", List.of("OPERATOR"));
        mockMvc.perform(put("/api/users/{id}/roles", adminId)
                        .with(user("stale-admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("roles", List.of("VIEWER")))))
                .andExpect(status().isConflict());
        assertThat(staleAdminId).isNotNull();
    }

    @Test
    void adminShouldResetOtherPasswordButNotOwnPassword() throws Exception {
        Long targetId = insertUser("reset-target", "OldPassword", List.of("OPERATOR"));
        Long adminId = userId("admin");
        var admin = user("admin").roles("ADMIN");

        mockMvc.perform(put("/api/users/{id}/password", targetId).with(admin).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("newPassword", "NewPassword"))))
                .andExpect(status().isNoContent());
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE id = ?", String.class, targetId);
        assertThat(passwordEncoder.matches("OldPassword", hash)).isFalse();
        assertThat(passwordEncoder.matches("NewPassword", hash)).isTrue();

        mockMvc.perform(put("/api/users/{id}/password", adminId).with(admin).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("newPassword", "OtherPassword"))))
                .andExpect(status().isConflict());

        String audit = jdbcTemplate.queryForObject(
                "SELECT detail_json FROM sys_operation_log WHERE operation = 'RESET_USER_PASSWORD' AND business_id = ?",
                String.class, targetId);
        assertThat(audit).contains("reset-target").doesNotContain("NewPassword", "password");
    }

    @Test
    void userShouldChangeOwnPasswordAndInvalidateCurrentSession() throws Exception {
        insertUser("self-user", "OldPassword", List.of("OPERATOR"));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(put("/api/users/me/password")
                        .session(session).with(user("self-user").roles("OPERATOR")).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("currentPassword", "OldPassword", "newPassword", "NewPassword"))))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        String audit = jdbcTemplate.queryForObject(
                "SELECT detail_json FROM sys_operation_log WHERE operation = 'CHANGE_OWN_PASSWORD'",
                String.class);
        assertThat(audit).contains("self-user").doesNotContain("OldPassword", "NewPassword", "password");

        mockMvc.perform(formLogin().user("self-user").password("OldPassword"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(formLogin().user("self-user").password("NewPassword"))
                .andExpect(authenticated().withUsername("self-user"));
    }

    @Test
    void userShouldRejectWrongOrUnchangedOwnPassword() throws Exception {
        insertUser("password-user", "OldPassword", List.of("VIEWER"));
        var viewer = user("password-user").roles("VIEWER");

        mockMvc.perform(put("/api/users/me/password").with(viewer).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("currentPassword", "WrongPassword", "newPassword", "NewPassword"))))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/users/me/password").with(viewer).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("currentPassword", "OldPassword", "newPassword", "OldPassword"))))
                .andExpect(status().isConflict());
    }

    private Long insertUser(String username, String password, List<String> roles) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (username, password_hash, display_name, status, deleted)
                VALUES (?, ?, ?, 1, 0)
                """, username, passwordEncoder.encode(password), username);
        Long userId = userId(username);
        for (String role : roles) {
            jdbcTemplate.update("""
                    INSERT INTO sys_user_role (user_id, role_id)
                    SELECT ?, id FROM sys_role WHERE role_code = ?
                    """, userId, role);
        }
        return userId;
    }

    private Long userId(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ? AND deleted = 0", Long.class, username);
    }

    private byte[] json(Object value) throws Exception {
        return objectMapper.writeValueAsBytes(value);
    }
}
