package com.shafting.weighing.device.dto;

import java.math.BigDecimal;

/** 任务模块启动设备模拟器时传入的运行参数。 */
public record SimulationStart(
        /** 测量运行主键。 */
        Long runId,
        /** 所属任务主键。 */
        Long taskId,
        /** 测量点主键。 */
        Long pointId,
        /** 测量点编码。 */
        String pointCode,
        /** 参数方案中的自动目标位移，单位 mm。 */
        BigDecimal maxDisplacementMm,
        /** 参数方案中的转速，单位 r/min。 */
        Integer motorRpm,
        /** 参数方案中的标准力，单位 kN。 */
        BigDecimal standardForceKn,
        /** 自动顶升累计力值间隔，单位 kN。 */
        BigDecimal forceIntervalKn,
        /** 每次达到累计力值间隔后的保持时间，单位秒。 */
        Integer holdSeconds) {
}
