package com.shafting.weighing.security.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 用户管理及密码维护接口的请求和响应对象集合。 */
public final class UserDto {

    private UserDto() {
    }

    /** 管理员新增用户请求。 */
    public record CreateRequest(
            /** 登录账号；服务端去除首尾空格并统一转为小写。 */
            @NotBlank(message = "登录账号不能为空") String username,
            /** 页面显示名称，长度为 1～64 个字符。 */
            @NotBlank(message = "显示名称不能为空") @Size(max = 64, message = "显示名称不能超过64位")
            String displayName,
            /** 初始明文密码，仅用于本次 BCrypt 计算，长度为 8～64 个字符。 */
            @NotBlank(message = "初始密码不能为空") @Size(min = 8, max = 64, message = "密码长度必须为8～64位")
            String password,
            /** 初始角色编码集合，至少包含一个有效角色且不能重复。 */
            @NotEmpty(message = "至少选择一个角色") List<@Valid @NotBlank(message = "角色编码不能为空") String> roles) {
    }

    /** 管理员修改已有用户角色请求。 */
    public record RoleUpdateRequest(
            /** 新角色编码集合，至少包含一个有效角色且不能重复。 */
            @NotEmpty(message = "至少选择一个角色") List<@Valid @NotBlank(message = "角色编码不能为空") String> roles) {
    }

    /** 管理员重置其他用户密码请求。 */
    public record PasswordResetRequest(
            /** 重置后的正式密码，长度为 8～64 个字符。 */
            @NotBlank(message = "新密码不能为空") @Size(min = 8, max = 64, message = "密码长度必须为8～64位")
            String newPassword) {
    }

    /** 当前用户修改本人密码请求。 */
    public record PasswordChangeRequest(
            /** 当前登录密码，用于确认是本人操作。 */
            @NotBlank(message = "原密码不能为空") String currentPassword,
            /** 与原密码不同的新密码，长度为 8～64 个字符。 */
            @NotBlank(message = "新密码不能为空") @Size(min = 8, max = 64, message = "密码长度必须为8～64位")
            String newPassword) {
    }

    /** 用户管理表格的一行用户信息，不返回密码摘要。 */
    public record UserResponse(
            /** 用户主键。 */ Long id,
            /** 小写登录账号。 */ String username,
            /** 页面显示名称。 */ String displayName,
            /** 账号状态：1 启用，0 停用。 */ Integer status,
            /** 按角色表顺序排列的角色编码。 */ List<String> roles) {
    }

    /** 用户管理页面可分配的角色选项。 */
    public record RoleResponse(
            /** 稳定角色编码。 */ String roleCode,
            /** 中文角色名称。 */ String roleName) {
    }
}
