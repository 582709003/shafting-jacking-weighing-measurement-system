package com.shafting.weighing.control.controller;

import com.shafting.weighing.control.dto.ControlDto;
import com.shafting.weighing.control.service.ControlCommandService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作页动作控制接口，对应互斥的手动模式区和自动模式区。 */
@RestController
@RequestMapping("/api/control")
public class ControlController {

    private final ControlCommandService commandService;

    public ControlController(ControlCommandService commandService) {
        this.commandService = commandService;
    }

    /**
     * 工作页加载或命令完成后读取后端权威控制状态。
     *
     * @return 报警锁定、全局动作及六个通道的模式、启动、暂停、清零和运行关联；所有登录角色可读
     */
    @GetMapping("/state")
    public ControlDto.ControlStateResponse state() {
        return commandService.state();
    }

    /**
     * 处理工作页模式、清零、手动启动与动作、暂停继续、停止、自动测试和自动快泄命令。
     *
     * <p>ADMIN、ENGINEER、OPERATOR 可调用，VIEWER 返回 403。参数或前置条件不满足返回
     * 400/409；响应始终是命令执行后的完整权威状态。</p>
     *
     * @param request 命令号、命令类型、可选测量点、目标位移和参数方案
     * @param authentication 当前登录用户，用于任务创建和操作审计
     * @return 命令完成后的控制状态
     */
    @PostMapping("/commands")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'OPERATOR')")
    public ControlDto.ControlStateResponse command(@Valid @RequestBody ControlDto.CommandRequest request,
                                                    Authentication authentication) {
        return commandService.execute(request, authentication.getName());
    }
}
