package com.shafting.weighing.curve.dto;

/** 自动选择或管理员拖动形成的一条拟合线段。 */
public record CurveSegment(
        /** 下端点：位移固定为 0 mm，管理员仅可调整顶升力横坐标。 */
        CurvePoint first,
        /** 上端点：管理员可调整顶升力横坐标和位移纵坐标，位移必须大于 0 mm。 */
        CurvePoint second) {
}
