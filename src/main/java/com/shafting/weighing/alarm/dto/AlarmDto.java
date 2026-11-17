package com.shafting.weighing.alarm.dto;

import com.shafting.weighing.alarm.entity.AlarmAction;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public final class AlarmDto {
    private AlarmDto() { }

    /** 报警处理按钮提交的备注。 */
    public record ActionRequest(
            /** 本次确认、恢复或关闭的说明，可为空。 */
            String remark) { }

    /** 设备模拟器注入报警的请求。 */
    public record SimulateRequest(
            /** 稳定的报警编码。 */
            @NotBlank String alarmCode,
            /** 报警级别，例如 WARNING 或 CRITICAL。 */
            @NotBlank String alarmLevel,
            /** 页面显示的设备或通道名称。 */
            @NotBlank String sourceName,
            /** 报警描述。 */
            @NotBlank String message) { }

    /** 报警详情页面或动作接口返回的数据。 */
    public record DetailResponse(
            /** 报警事件主键。 */
            Long id,
            /** 报警编码。 */
            String alarmCode,
            /** 报警级别。 */
            String alarmLevel,
            /** 报警来源设备或通道。 */
            String sourceName,
            /** 报警描述。 */
            String message,
            /** 当前处理状态。 */
            String status,
            /** 按时间排列的处理动作。 */
            List<AlarmAction> actions) { }
}
