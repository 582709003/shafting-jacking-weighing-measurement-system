package com.shafting.weighing.task.dto;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.NotNull;

public final class TaskDto {

    private TaskDto() {
    }

    /** 工作页新建测量任务的请求。 */
    public record CreateTaskRequest(
            /** 页面显示和记录查询使用的任务名称。 */
            @NotBlank String taskName,
            /** 本次任务采用的参数方案主键。 */
            @NotNull Long configId,
            /** 用户当前选择的六点测量点编码。 */
            @NotBlank String pointCode) {
    }

    /** 工作页发送运行控制命令的请求。 */
    public record CommandRequest(
            /** 客户端生成的唯一命令号，用于幂等控制。 */
            @NotBlank String commandNo,
            /** 需要执行的状态机命令。 */
            @NotNull CommandType commandType) {
    }

    /** 工作页和任务查询接口共用的任务状态响应。 */
    public record TaskResponse(
            /** 测量任务主键。 */
            Long taskId,
            /** 本次测量运行主键，实时事件和曲线接口使用该值。 */
            Long runId,
            /** 系统生成的唯一任务编号。 */
            String taskNo,
            /** 用户填写的任务名称。 */
            String taskName,
            /** 测量点稳定编码。 */
            String pointCode,
            /** 测量点页面显示名称。 */
            String pointName,
            /** 运行模式：SIMULATED、MANUAL 或 AUTOMATIC。 */
            String mode,
            /** 控制状态，例如 IDLE、RUNNING、PAUSED、COMPLETED。 */
            String state,
            /** 自动流程阶段，例如 PRECHECK、LIFTING、HOLDING、LOWERING。 */
            String processStage) {
    }

    /** 工作页支持的运行控制命令。 */
    public enum CommandType {
        /** 从待启动状态开始运行。 */
        START,
        /** 暂停当前运行并保留已采样数据。 */
        PAUSE,
        /** 从暂停位置继续运行。 */
        RESUME,
        /** 停止当前运行，不再自动继续。 */
        STOP
    }
}
