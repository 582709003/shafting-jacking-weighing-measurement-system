package com.shafting.weighing.alarm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("alarm_event")
/** 报警页面展示的一条报警事件。 */
public class AlarmEvent {
    /** 报警事件主键。 */
    @TableId(type = IdType.AUTO) private Long id;
    /** 稳定报警编码。 */
    @TableField("alarm_code") private String alarmCode;
    /** 报警级别，例如 WARNING、CRITICAL。 */
    @TableField("alarm_level") private String alarmLevel;
    /** 报警设备或通道显示名称。 */
    @TableField("source_name") private String sourceName;
    /** 报警描述。 */
    private String message;
    /** ACTIVE、ACKNOWLEDGED、RECOVERED 或 CLOSED。 */
    private String status;
    /** 报警发生时间。 */
    @TableField("occurred_at") private LocalDateTime occurredAt;
    /** 最近一次状态变更时间。 */
    @TableField("updated_at") private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getAlarmCode() { return alarmCode; }
    public void setAlarmCode(String value) { alarmCode = value; }
    public String getAlarmLevel() { return alarmLevel; }
    public void setAlarmLevel(String value) { alarmLevel = value; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String value) { sourceName = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime value) { occurredAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
