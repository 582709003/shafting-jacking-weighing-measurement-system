package com.shafting.weighing.security.controller;

import com.shafting.weighing.security.dto.UserDto;
import com.shafting.weighing.security.service.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理页面及顶部“修改密码”对话框使用的接口。
 *
 * <p>用户查询、新增、角色调整和他人密码重置仅 ADMIN 可用；本人密码修改允许所有已登录用户调用。
 * 所有修改请求均由 Spring Security 校验 CSRF。</p>
 */
@RestController
@RequestMapping("/api/users")
public class UserManagementController {

    private final UserManagementService userManagementService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    /**
     * 用户管理页面加载全部未删除用户及角色。
     *
     * @return 用户表格数据，不包含密码摘要
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto.UserResponse> listUsers() {
        return userManagementService.listUsers();
    }

    /**
     * 新增用户对话框加载可分配角色。
     *
     * @return 按数据库顺序排列的角色编码和中文名称
     */
    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto.RoleResponse> listRoles() {
        return userManagementService.listRoles();
    }

    /**
     * 管理员新增启用用户并分配一个或多个角色。
     *
     * @param request 账号、显示名称、初始密码和角色
     * @param authentication 当前管理员身份
     * @return 新建用户信息
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto.UserResponse create(@Valid @RequestBody UserDto.CreateRequest request,
                                       Authentication authentication) {
        return userManagementService.create(request, authentication.getName());
    }

    /**
     * 管理员调整其他用户角色；禁止修改本人角色或移除最后一个管理员。
     *
     * @param userId 目标用户主键
     * @param request 新角色集合
     * @param authentication 当前管理员身份
     * @return 更新后的用户信息
     */
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto.UserResponse updateRoles(@PathVariable Long userId,
                                            @Valid @RequestBody UserDto.RoleUpdateRequest request,
                                            Authentication authentication) {
        return userManagementService.updateRoles(userId, request, authentication.getName());
    }

    /**
     * 管理员将其他用户密码重置为正式密码；已登录会话按产品约定不主动失效。
     *
     * @param userId 目标用户主键
     * @param request 新密码
     * @param authentication 当前管理员身份
     */
    @PutMapping("/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void resetPassword(@PathVariable Long userId,
                              @Valid @RequestBody UserDto.PasswordResetRequest request,
                              Authentication authentication) {
        userManagementService.resetPassword(userId, request, authentication.getName());
    }

    /**
     * 当前用户验证原密码后修改本人密码，成功后立即注销当前会话并要求重新登录。
     *
     * @param request 原密码和新密码
     * @param authentication 当前登录身份
     * @param servletRequest 当前 HTTP 请求，用于注销会话
     * @param servletResponse 当前 HTTP 响应
     */
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeOwnPassword(@Valid @RequestBody UserDto.PasswordChangeRequest request,
                                  Authentication authentication,
                                  HttpServletRequest servletRequest,
                                  HttpServletResponse servletResponse) {
        userManagementService.changeOwnPassword(request, authentication.getName());
        logoutHandler.logout(servletRequest, servletResponse, authentication);
    }
}
