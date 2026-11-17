package com.shafting.weighing.alarm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("alarm_action")
/** 报警确认、恢复或关闭的不可覆盖处理记录。 */
public class AlarmAction {
    /** 动作记录主键。 */
    @TableId(type = IdType.AUTO) private Long id;
    /** 所属报警事件主键。 */
    @TableField("alarm_id") private Long alarmId;
    /** ACKNOWLEDGE、RECOVER 或 CLOSE。 */
    @TableField("action_type") private String actionType;
    /** 操作前状态。 */
    @TableField("from_status") private String fromStatus;
    /** 操作后状态。 */
    @TableField("to_status") private String toStatus;
    /** 操作人填写的处理说明。 */
    private String remark;
    /** 操作用户主键。 */
    @TableField("acted_by") private Long actedBy;
    /** 操作时间。 */
    @TableField("acted_at") private LocalDateTime actedAt;

    public Long getId() { return id; }
    public void setAlarmId(Long value) { alarmId = value; }
    public String getActionType() { return actionType; }
    public void setActionType(String value) { actionType = value; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String value) { fromStatus = value; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String value) { toStatus = value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { remark = value; }
    public void setActedBy(Long value) { actedBy = value; }
    public LocalDateTime getActedAt() { return actedAt; }
    public void setActedAt(LocalDateTime value) { actedAt = value; }
}
