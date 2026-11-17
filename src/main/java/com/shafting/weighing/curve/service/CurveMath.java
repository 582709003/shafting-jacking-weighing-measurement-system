package com.shafting.weighing.curve.service;

import com.shafting.weighing.curve.dto.CurvePoint;
import com.shafting.weighing.curve.dto.CurveSegment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 曲线拟合使用的纯数学函数。 */
public final class CurveMath {

    private CurveMath() {
    }

    /**
     * 从按采样顺序排列的点中选择位移/力斜率绝对值最大的相邻两点。
     *
     * <p>只比较相邻点是已确认的业务规则；力差或位移差为 0 的点对无有效斜率，直接跳过。</p>
     *
     * @param points 同一升降阶段的有效采样点
     * @return 选中的相邻点线段
     */
    public static CurveSegment fitAdjacent(List<CurvePoint> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("曲线拟合至少需要两个相邻采样点");
        }
        CurveSegment selected = null;
        BigDecimal maximumSlope = BigDecimal.valueOf(-1);
        for (int index = 0; index < points.size() - 1; index++) {
            CurveSegment candidate = new CurveSegment(points.get(index), points.get(index + 1));
            BigDecimal deltaForce = candidate.second().forceKn().subtract(candidate.first().forceKn()).abs();
            BigDecimal deltaDisplacement = candidate.second().displacementMm()
                    .subtract(candidate.first().displacementMm()).abs();
            if (deltaForce.signum() == 0 || deltaDisplacement.signum() == 0) {
                continue;
            }
            BigDecimal slope = deltaDisplacement.divide(deltaForce, 12, RoundingMode.HALF_UP);
            if (slope.compareTo(maximumSlope) > 0) {
                maximumSlope = slope;
                selected = candidate;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("相邻采样点不能形成有效拟合线");
        }
        return selected;
    }

    /**
     * 计算拟合线延长后与位移为 0 的力轴交点。
     *
     * @param segment 两个拟合端点
     * @return 力轴截距，单位 kN，保留两位小数
     */
    public static BigDecimal forceIntercept(CurveSegment segment) {
        BigDecimal deltaDisplacement = segment.second().displacementMm()
                .subtract(segment.first().displacementMm());
        if (deltaDisplacement.signum() == 0) {
            throw new IllegalArgumentException("拟合线端点位移不能相同");
        }
        BigDecimal deltaForce = segment.second().forceKn().subtract(segment.first().forceKn());
        return segment.first().forceKn().subtract(segment.first().displacementMm()
                        .multiply(deltaForce).divide(deltaDisplacement, 12, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 将相邻点形成的拟合直线延长为“力轴交点—最高位移点”线段。
     *
     * <p>曲线页只允许拖动这两个端点，因此自动拟合结果也必须使用相同的端点定义。</p>
     *
     * @param segment 相邻采样点确定的拟合直线
     * @param maximumDisplacement 本次运行全部有效采样中的最高位移，单位 mm
     * @return 第一个端点位于位移 0，第二个端点位于最高位移
     */
    public static CurveSegment anchorAtMaximumDisplacement(CurveSegment segment,
                                                            BigDecimal maximumDisplacement) {
        if (maximumDisplacement == null || maximumDisplacement.signum() <= 0) {
            throw new IllegalArgumentException("最高位移必须大于 0");
        }
        BigDecimal deltaDisplacement = segment.second().displacementMm()
                .subtract(segment.first().displacementMm());
        if (deltaDisplacement.signum() == 0) {
            throw new IllegalArgumentException("拟合线端点位移不能相同");
        }
        BigDecimal deltaForce = segment.second().forceKn().subtract(segment.first().forceKn());
        BigDecimal upperForce = segment.first().forceKn()
                .add(maximumDisplacement.subtract(segment.first().displacementMm())
                        .multiply(deltaForce).divide(deltaDisplacement, 12, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
        return new CurveSegment(
                new CurvePoint(forceIntercept(segment), BigDecimal.ZERO.setScale(2)),
                new CurvePoint(upperForce, maximumDisplacement.setScale(2, RoundingMode.HALF_UP)));
    }
}
