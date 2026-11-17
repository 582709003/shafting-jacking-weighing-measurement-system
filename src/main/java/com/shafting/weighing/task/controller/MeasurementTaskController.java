package com.shafting.weighing.task.controller;

import com.shafting.weighing.task.dto.TaskDto;
import com.shafting.weighing.task.service.MeasurementTaskService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
/** 测量任务和运行命令接口，对应前端“轴系工作界面”。 */
public class MeasurementTaskController {

    private final MeasurementTaskService measurementTaskService;

    public MeasurementTaskController(MeasurementTaskService measurementTaskService) {
        this.measurementTaskService = measurementTaskService;
    }

    /**
     * 点击“开始模拟测试”时创建任务及其首个运行记录。
     *
     * @param request 页面选择的测量点、任务名称和参数方案
     * @param authentication 当前登录用户，用作任务操作人
     * @return 新任务及运行标识
     */
    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'OPERATOR')")
    public TaskDto.TaskResponse createTask(@Valid @RequestBody TaskDto.CreateTaskRequest request,
                                           Authentication authentication) {
        return measurementTaskService.createTask(request, authentication.getName());
    }

    /**
     * 读取工作页或记录页选中的任务状态。
     *
     * @param taskId 测量任务主键
     * @return 任务和运行状态
     */
    @GetMapping("/tasks/{taskId}")
    public TaskDto.TaskResponse getTask(@PathVariable Long taskId) {
        return measurementTaskService.getTask(taskId);
    }

    /**
     * 执行工作页的启动、暂停、继续或停止命令。
     *
     * <p>命令号用于防止浏览器重试造成重复执行；状态不允许跳转时返回 409。</p>
     *
     * @param runId 测量运行主键
     * @param request 命令号及命令类型
     * @return 命令执行后的任务状态
     */
    @PostMapping("/runs/{runId}/commands")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'OPERATOR')")
    public TaskDto.TaskResponse command(@PathVariable Long runId,
                                        @Valid @RequestBody TaskDto.CommandRequest request) {
        return measurementTaskService.command(runId, request);
    }
}
