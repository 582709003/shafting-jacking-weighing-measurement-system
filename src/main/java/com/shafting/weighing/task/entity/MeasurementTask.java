package com.shafting.weighing.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("test_task")
/** 用户在工作页创建的一次测量任务。 */
public class MeasurementTask {

    /** 任务主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 系统生成的唯一任务编号。 */
    @TableField("task_no")
    private String taskNo;

    /** 用户填写的任务名称。 */
    @TableField("task_name")
    private String taskName;

    /** 本次任务采用的参数方案主键。 */
    @TableField("config_id")
    private Long configId;

    /** 创建任务的用户主键。 */
    @TableField("operator_id")
    private Long operatorId;

    /** 任务级状态，与当前运行的控制状态同步。 */
    @TableField("task_status")
    private String taskStatus;

    /** 首次启动时间。 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 自动完成或人工停止时间。 */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    /** MyBatis-Plus 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
