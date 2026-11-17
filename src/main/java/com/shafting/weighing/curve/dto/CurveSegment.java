package com.shafting.weighing.curve.dto;

/** 自动选择或管理员拖动形成的一条拟合线段。 */
public record CurveSegment(
        /** 下端点：位移固定为 0 mm，管理员仅可调整顶升力横坐标。 */
        CurvePoint first,
        /** 上端点：位移固定为本次采样最高位移，管理员仅可调整顶升力横坐标。 */
        CurvePoint second) {
}
