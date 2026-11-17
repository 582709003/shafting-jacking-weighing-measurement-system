package com.shafting.weighing.device.dto;

import com.shafting.weighing.control.dto.ControlDto;

import java.math.BigDecimal;

/** 手动动作首次创建模拟器会话时使用的运行参数。 */
public record ManualSimulationStart(
        /** 测量运行主键。 */
        Long runId,
        /** 所属任务主键。 */
        Long taskId,
        /** 测量点主键。 */
        Long pointId,
        /** 测量点稳定编码。 */
        String pointCode,
        /** 参数方案最大位移，单位 mm。 */
        BigDecimal maxDisplacementMm,
        /** 模拟峰值顶升力，单位 kN。 */
        BigDecimal maxForceKn,
        /** 首次手动动作。 */
        ControlDto.ChannelAction action,
        /** 手动上升目标位移，单位 mm；非上升动作可为空。 */
        BigDecimal targetDisplacementMm) {
}
