package com.shafting.weighing.curve.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CurveDto {
    private CurveDto() { }

    /** 管理员在曲线页保存拖动结果的请求。 */
    public record ManualRequest(
            /** 上升曲线的人工拟合线段。 */
            @Valid @NotNull CurveSegment lifting,
            /** 下降曲线的人工拟合线段。 */
            @Valid @NotNull CurveSegment lowering) { }

    /** 管理员在显示数值页修改本次测量调整系数的请求。 */
    public record AdjustmentCoefficientRequest(
            /** 本次测量采用的调整系数，无单位、精度 0.0001，必须大于 0。 */
            @NotNull @Positive @Digits(integer = 8, fraction = 4) BigDecimal adjustmentCoefficient) { }

    /** 曲线页面和数值页面共用的拟合结果。 */
    public record FitResponse(
            /** 当前修订号，每次自动拟合或人工保存后递增。 */
            Integer revisionNo,
            /** 修订来源：AUTO 或 MANUAL。 */
            String source,
            /** 上升拟合线段。 */
            CurveSegment lifting,
            /** 下降拟合线段。 */
            CurveSegment lowering,
            /** 上升拟合线与力轴的截距，单位 kN。 */
            BigDecimal liftingInterceptKn,
            /** 下降拟合线与力轴的截距，单位 kN。 */
            BigDecimal loweringInterceptKn,
            /** 按截距平均值乘顶升系数得到的测量值，单位 kN。 */
            BigDecimal measuredForceKn,
            /** 参数方案中的标准值，单位 kN。 */
            BigDecimal standardForceKn,
            /** 测量值减标准值，单位 kN。 */
            BigDecimal deviationKn,
            /** 偏差量除以标准值的百分比；标准值为 0 时为空。 */
            BigDecimal deviationRate,
            /** 本次测量采用的调整系数；仅管理员响应包含该字段。 */
            @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal adjustmentCoefficient,
            /** 调整量，因原始公式尚未确认，当前固定为空。 */
            BigDecimal adjustmentAmountMm) {

        /** 对非管理员隐藏调整系数，避免前端隐藏被绕过。 */
        public FitResponse withoutAdjustmentCoefficient() {
            return new FitResponse(revisionNo, source, lifting, lowering, liftingInterceptKn,
                    loweringInterceptKn, measuredForceKn, standardForceKn, deviationKn,
                    deviationRate, null, adjustmentAmountMm);
        }
    }

    /** 显示数值页选择通道后返回的最新一次测量结果。 */
    public record ValuePageResponse(
            /** 测量任务主键。 */
            Long taskId,
            /** 测量运行主键，保存、删除及曲线采样接口使用该值。 */
            Long runId,
            /** 系统生成的任务编号。 */
            String taskNo,
            /** 用户可识别的任务名称。 */
            String taskName,
            /** 测量点稳定编码。 */
            String pointCode,
            /** 测量点页面显示名称。 */
            String pointName,
            /** 运行状态，例如 IDLE、COMPLETED 或 STOPPED。 */
            String runState,
            /** 结果状态：DRAFT 待保存，SAVED 已保存。 */
            String resultStatus,
            /** 最近保存确认时间；DRAFT 时为空。 */
            LocalDateTime savedAt,
            /** 标准值、测量值和偏差等计算结果。 */
            FitResponse result) {

        /** 对非管理员返回不含调整系数的结果。 */
        public ValuePageResponse withoutAdjustmentCoefficient() {
            return new ValuePageResponse(taskId, runId, taskNo, taskName, pointCode, pointName,
                    runState, resultStatus, savedAt, result.withoutAdjustmentCoefficient());
        }
    }
}
