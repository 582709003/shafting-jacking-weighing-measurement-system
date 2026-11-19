package com.shafting.weighing.security.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shafting.weighing.measurement.mapper.OperationLogMapper;
import com.shafting.weighing.security.dto.UserDto;
import com.shafting.weighing.security.entity.SysRole;
import com.shafting.weighing.security.entity.SysUser;
import com.shafting.weighing.security.mapper.SysRoleMapper;
import com.shafting.weighing.security.mapper.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 用户查询、新增、角色维护和密码更新的业务服务。 */
@Service
public class UserManagementService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9._-]{3,64}");

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public UserManagementService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                                 PasswordEncoder passwordEncoder, OperationLogMapper operationLogMapper,
                                 ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    /** 返回全部未删除用户及其角色，供管理员页面展示。 */
    public List<UserDto.UserResponse> listUsers() {
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery().orderByAsc(SysUser::getId)).stream()
                .map(this::response)
                .toList();
    }

    /** 返回角色表中的全部可分配角色，保持数据库定义顺序。 */
    public List<UserDto.RoleResponse> listRoles() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId)).stream()
                .map(role -> new UserDto.RoleResponse(role.getRoleCode(), role.getRoleName()))
                .toList();
    }

    /** 新增启用用户并绑定角色；账号、用户和审计在同一事务提交。 */
    @Transactional
    public UserDto.UserResponse create(UserDto.CreateRequest request, String operatorUsername) {
        String username = normalizeUsername(request.username());
        List<String> roles = validateRoles(request.roles());
        if (userMapper.selectIdByUsername(username) != null) {
            throw conflict("登录账号已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setDisplayName(request.displayName().strip());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw conflict("登录账号已存在");
        }
        replaceRoles(user.getId(), roles);
        audit(operatorUsername, "CREATE_USER", user.getId(), Map.of("username", username, "roles", roles));
        return response(user);
    }

    /** 修改其他用户角色；禁止修改本人或移除系统最后一个管理员。 */
    @Transactional
    public UserDto.UserResponse updateRoles(Long userId, UserDto.RoleUpdateRequest request,
                                            String operatorUsername) {
        SysUser target = requireUser(userId);
        Long operatorId = requireUserId(operatorUsername);
        if (operatorId.equals(userId)) {
            throw conflict("不能修改自己的角色");
        }
        List<String> oldRoles = userMapper.selectRoleCodes(userId);
        List<String> newRoles = validateRoles(request.roles());
        if (oldRoles.contains("ADMIN") && !newRoles.contains("ADMIN")
                && userMapper.countEnabledAdministrators() <= 1) {
            throw conflict("系统必须至少保留一名启用的管理员");
        }
        replaceRoles(userId, newRoles);
        audit(operatorUsername, "UPDATE_USER_ROLES", userId,
                Map.of("username", target.getUsername(), "roles", newRoles));
        return response(target);
    }

    /** 管理员重置其他用户的正式密码，不记录明文密码。 */
    @Transactional
    public void resetPassword(Long userId, UserDto.PasswordResetRequest request, String operatorUsername) {
        SysUser target = requireUser(userId);
        if (requireUserId(operatorUsername).equals(userId)) {
            throw conflict("请通过修改本人密码功能更新自己的密码");
        }
        target.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(target);
        audit(operatorUsername, "RESET_USER_PASSWORD", userId, Map.of("username", target.getUsername()));
    }

    /** 校验原密码后更新当前用户密码；会话注销由 Controller 在事务成功后执行。 */
    @Transactional
    public void changeOwnPassword(UserDto.PasswordChangeRequest request, String username) {
        SysUser user = requireUser(requireUserId(username));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw conflict("原密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw conflict("新密码不能与原密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
        audit(username, "CHANGE_OWN_PASSWORD", user.getId(), Map.of("username", user.getUsername()));
    }

    private String normalizeUsername(String username) {
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "登录账号必须为3～64位小写字母、数字、点、下划线或连字符");
        }
        return normalized;
    }

    private List<String> validateRoles(List<String> requestedRoles) {
        List<String> normalized = requestedRoles.stream()
                .map(role -> role.strip().toUpperCase(Locale.ROOT))
                .toList();
        Set<String> unique = new LinkedHashSet<>(normalized);
        if (unique.size() != normalized.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不能重复");
        }
        Set<String> available = roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()).stream()
                .map(SysRole::getRoleCode)
                .collect(java.util.stream.Collectors.toSet());
        if (unique.isEmpty() || !available.containsAll(unique)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "包含不存在的角色");
        }
        return unique.stream().sorted((left, right) -> Integer.compare(roleOrder(left), roleOrder(right))).toList();
    }

    private int roleOrder(String roleCode) {
        return switch (roleCode) {
            case "ADMIN" -> 1;
            case "ENGINEER" -> 2;
            case "OPERATOR" -> 3;
            case "VIEWER" -> 4;
            default -> Integer.MAX_VALUE;
        };
    }

    private void replaceRoles(Long userId, List<String> roles) {
        userMapper.deleteUserRoles(userId);
        roles.forEach(role -> userMapper.insertUserRole(userId, role));
    }

    private UserDto.UserResponse response(SysUser user) {
        return new UserDto.UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getStatus(),
                List.copyOf(userMapper.selectRoleCodes(user.getId())));
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private Long requireUserId(String username) {
        Long userId = userMapper.selectIdByUsername(username.strip().toLowerCase(Locale.ROOT));
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前登录用户不存在");
        }
        return userId;
    }

    private void audit(String operatorUsername, String operation, Long targetUserId, Map<String, Object> detail) {
        try {
            operationLogMapper.insertOperation(requireUserId(operatorUsername), "USER", operation,
                    targetUserId, objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("用户操作审计序列化失败", exception);
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
