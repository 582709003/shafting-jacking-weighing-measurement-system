package com.shafting.weighing.alarm.dto;

import com.shafting.weighing.alarm.entity.AlarmAction;
import com.shafting.weighing.alarm.entity.AlarmEvent;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
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

    /** 报警页面分页查询结果，包含三个状态页签的未筛选数量。 */
    public record PageResponse(
            /** 当前页报警事件，按发生时间倒序排列。 */
            List<AlarmEvent> events,
            /** 当前页码，从 1 开始。 */
            int page,
            /** 每页记录数。 */
            int pageSize,
            /** 当前页签及筛选条件对应的记录总数。 */
            long total,
            /** 总页数；没有记录时仍返回 1。 */
            int totalPages,
            /** ACTIVE 状态报警总数，用于顶部红色角标。 */
            long activeCount,
            /** ACKNOWLEDGED、RECOVERED 状态报警总数。 */
            long processingCount,
            /** CLOSED 状态报警总数。 */
            long closedCount) { }

    /** SSE 推送的一次新报警通知；历史未关闭报警不会在重新连接时重放。 */
    public record NotificationResponse(
            /** 新报警事件主键，用于浏览器防止重复提示。 */
            Long id,
            /** 稳定报警编码。 */
            String alarmCode,
            /** 报警级别。 */
            String alarmLevel,
            /** 报警来源设备或通道。 */
            String sourceName,
            /** 报警内容。 */
            String message,
            /** 报警发生时间。 */
            LocalDateTime occurredAt,
            /** 推送时数据库中的活动报警数量。 */
            long activeCount) { }
}
