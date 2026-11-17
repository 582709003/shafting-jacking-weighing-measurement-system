package com.shafting.weighing.security.controller;

import org.springframework.stereotype.Controller;

import org.springframework.web.bind.annotation.GetMapping;

@Controller
/** 登录页面控制器。 */
public class LoginController {

    /**
     * 打开系统登录页。
     *
     * @return Thymeleaf 模板名称
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
