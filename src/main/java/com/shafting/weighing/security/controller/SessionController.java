package com.shafting.weighing.security.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/session")
/**
 * 当前会话接口，所有业务页面初始化时首先调用。
 *
 * <p>前端依据角色控制参数编辑、运行命令和曲线端点拖动权限，
 * 并使用返回的 CSRF 信息提交修改类请求。</p>
 */
public class SessionController {

    /**
     * 返回当前登录用户、角色和 CSRF 请求信息。
     *
     * @param authentication Spring Security 当前认证信息
     * @param csrfToken 当前会话的 CSRF 令牌
     * @return 前端初始化所需的会话上下文
     */
    @GetMapping
    public SessionResponse currentSession(Authentication authentication, CsrfToken csrfToken) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .sorted()
                .toList();
        return new SessionResponse(authentication.getName(), roles,
                csrfToken.getHeaderName(), csrfToken.getToken());
    }

    /** 页面初始化使用的登录会话信息。 */
    public record SessionResponse(
            /** 当前登录账号。 */
            String username,
            /** 不带 ROLE_ 前缀的角色编码集合。 */
            List<String> roles,
            /** 修改类请求应使用的 CSRF 请求头名称。 */
            String csrfHeaderName,
            /** 修改类请求应携带的 CSRF 令牌。 */
            String csrfToken) {
    }
}
