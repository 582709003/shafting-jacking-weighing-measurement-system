package com.shafting.weighing.config;

import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpStatus;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import org.springframework.security.web.AuthenticationEntryPoint;

import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
/** 系统登录、接口认证、未登录响应和密码摘要配置。 */
public class SecurityConfig {

    /**
     * 配置页面登录和 API 认证行为。
     *
     * <p>未登录访问 /api/** 返回 401，访问业务页面跳转登录页；
     * 具体角色限制由各 Controller 的 {@code @PreAuthorize} 声明。</p>
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint apiAuthenticationEntryPoint = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/prototype/styles.css", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(apiAuthenticationEntryPoint,
                                new AntPathRequestMatcher("/api/**"))
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
                                new AntPathRequestMatcher("/prototype/**")))
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/prototype/index.html", true))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }

    /** 使用 BCrypt 保存和校验密码，不存储明文。 */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

