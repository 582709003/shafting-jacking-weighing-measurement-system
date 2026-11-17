package com.shafting.weighing.device.dto;

import java.math.BigDecimal;

import java.time.Instant;

/** 工作页通过 SSE 接收的一次实时测量快照。 */
public record RealtimeSnapshot(
        /** 测量运行主键。 */
        Long runId,
        /** 所属测量任务主键。 */
        Long taskId,
        /** 当前运行的测量点编码。 */
        String pointCode,
        /** 运行控制状态，例如 RUNNING、PAUSED、COMPLETED。 */
        String state,
        /** 自动测量流程阶段。 */
        String processStage,
        /** 当前位移，单位 mm，保留 0.01 mm 分度。 */
        BigDecimal displacementMm,
        /** 当前压力，单位 MPa。 */
        BigDecimal pressureMpa,
        /** 当前电机转速，单位 r/min。 */
        Integer motorRpm,
        /** 当前顶升力，单位 kN。 */
        BigDecimal forceKn,
        /** 本次快照采集时间。 */
        Instant capturedAt) {
}
