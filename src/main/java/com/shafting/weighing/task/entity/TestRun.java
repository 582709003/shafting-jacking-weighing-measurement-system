package com.shafting.weighing.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("test_run")
/** 任务下的一次设备运行及其控制状态。 */
public class TestRun {

    /** 运行主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属测量任务主键。 */
    @TableField("task_id")
    private Long taskId;

    /** 本次运行的测量点主键。 */
    @TableField("point_id")
    private Long pointId;

    /** 系统生成的运行编号。 */
    @TableField("run_no")
    private String runNo;

    /** 运行模式：SIMULATED、MANUAL 或 AUTOMATIC。 */
    private String mode;

    /** 控制状态：IDLE、RUNNING、PAUSED、STOPPED、COMPLETED。 */
    private String state;

    /** 自动流程阶段：WAITING、PRECHECK、LIFTING、HOLDING、LOWERING、COMPLETED。 */
    @TableField("process_stage")
    private String processStage;

    /** 首次启动时间。 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 停止或完成时间。 */
    @TableField("ended_at")
    private LocalDateTime endedAt;

    /** 设备故障编码；真实设备未接入前为空。 */
    @TableField("fault_code")
    private String faultCode;

    /** MyBatis-Plus 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
    }

    public String getRunNo() {
        return runNo;
    }

    public void setRunNo(String runNo) {
        this.runNo = runNo;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getProcessStage() {
        return processStage;
    }

    public void setProcessStage(String processStage) {
        this.processStage = processStage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public String getFaultCode() {
        return faultCode;
    }

    public void setFaultCode(String faultCode) {
        this.faultCode = faultCode;
    }
}
