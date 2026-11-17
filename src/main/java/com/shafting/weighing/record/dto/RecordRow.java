package com.shafting.weighing.record.dto;

import com.shafting.weighing.record.mapper.RecordMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 记录页面一行任务摘要，由 {@link RecordMapper} 的关联查询填充。 */
public class RecordRow {
    /** 测量任务主键。 */
    private Long taskId;
    /** 测量运行主键，用于进入曲线和结果页面。 */
    private Long runId;
    /** 系统生成的任务编号。 */
    private String taskNo;
    /** 用户填写的任务名称。 */
    private String taskName;
    /** 测量点显示名称。 */
    private String pointName;
    /** 测量点稳定编码，供曲线页按通道筛选已完成任务。 */
    private String pointCode;
    /** 当前运行状态。 */
    private String state;
    /** 最新测量值，尚未拟合时为空。 */
    private BigDecimal measuredForceKn;
    /** 任务首次启动时间。 */
    private LocalDateTime startedAt;
    /** 自动完成或人工停止时间。 */
    private LocalDateTime completedAt;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public Long getRunId() { return runId; }
    public void setRunId(Long value) { runId = value; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String value) { taskNo = value; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String value) { taskName = value; }
    public String getPointName() { return pointName; }
    public void setPointName(String value) { pointName = value; }
    public String getPointCode() { return pointCode; }
    public void setPointCode(String value) { pointCode = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public BigDecimal getMeasuredForceKn() { return measuredForceKn; }
    public void setMeasuredForceKn(BigDecimal value) { measuredForceKn = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { completedAt = value; }
}
