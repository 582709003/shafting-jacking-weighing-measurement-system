package com.shafting.weighing.curve.dto;

import java.math.BigDecimal;

/** 曲线坐标系中的一个“顶升力—位移”点。 */
public record CurvePoint(
        /** 横坐标顶升力，单位 kN。 */
        BigDecimal forceKn,
        /** 纵坐标位移，单位 mm。 */
        BigDecimal displacementMm) {
}
