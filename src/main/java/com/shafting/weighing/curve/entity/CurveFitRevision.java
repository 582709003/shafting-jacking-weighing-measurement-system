package com.shafting.weighing.curve.entity;

import com.shafting.weighing.curve.dto.CurvePoint;
import com.shafting.weighing.curve.dto.CurveSegment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@TableName("curve_fit_revision")
/** 一次自动拟合或管理员人工调整形成的不可覆盖修订。 */
public class CurveFitRevision {

    /** 修订记录主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属测量运行主键。 */
    @TableField("run_id")
    private Long runId;
    /** 运行内递增的修订号。 */
    @TableField("revision_no")
    private Integer revisionNo;
    /** AUTO 或 MANUAL。 */
    private String source;
    /** 上升线第一个端点的力坐标。 */
    @TableField("lifting_x1") private BigDecimal liftingX1;
    /** 上升线第一个端点的位移坐标。 */
    @TableField("lifting_y1") private BigDecimal liftingY1;
    /** 上升线第二个端点的力坐标。 */
    @TableField("lifting_x2") private BigDecimal liftingX2;
    /** 上升线第二个端点的位移坐标。 */
    @TableField("lifting_y2") private BigDecimal liftingY2;
    /** 下降线第一个端点的力坐标。 */
    @TableField("lowering_x1") private BigDecimal loweringX1;
    /** 下降线第一个端点的位移坐标。 */
    @TableField("lowering_y1") private BigDecimal loweringY1;
    /** 下降线第二个端点的力坐标。 */
    @TableField("lowering_x2") private BigDecimal loweringX2;
    /** 下降线第二个端点的位移坐标。 */
    @TableField("lowering_y2") private BigDecimal loweringY2;
    /** 上升拟合线的力轴截距。 */
    @TableField("lifting_intercept") private BigDecimal liftingIntercept;
    /** 下降拟合线的力轴截距。 */
    @TableField("lowering_intercept") private BigDecimal loweringIntercept;
    /** 两个截距的算术平均值。 */
    @TableField("mean_intercept") private BigDecimal meanIntercept;
    /** 创建该修订的用户主键。 */
    @TableField("created_by") private Long createdBy;

    public void setRunId(Long value) { runId = value; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer value) { revisionNo = value; }
    public String getSource() { return source; }
    public void setSource(String value) { source = value; }
    public void setCreatedBy(Long value) { createdBy = value; }
    /** 将领域线段拆分为数据库坐标字段。 */
    public void setSegments(CurveSegment lifting, CurveSegment lowering) {
        liftingX1 = lifting.first().forceKn(); liftingY1 = lifting.first().displacementMm();
        liftingX2 = lifting.second().forceKn(); liftingY2 = lifting.second().displacementMm();
        loweringX1 = lowering.first().forceKn(); loweringY1 = lowering.first().displacementMm();
        loweringX2 = lowering.second().forceKn(); loweringY2 = lowering.second().displacementMm();
    }
    /** 还原上升拟合线段。 */
    public CurveSegment lifting() { return segment(liftingX1, liftingY1, liftingX2, liftingY2); }
    /** 还原下降拟合线段。 */
    public CurveSegment lowering() { return segment(loweringX1, loweringY1, loweringX2, loweringY2); }
    public BigDecimal getLiftingIntercept() { return liftingIntercept; }
    public void setLiftingIntercept(BigDecimal value) { liftingIntercept = value; }
    public BigDecimal getLoweringIntercept() { return loweringIntercept; }
    public void setLoweringIntercept(BigDecimal value) { loweringIntercept = value; }
    public BigDecimal getMeanIntercept() { return meanIntercept; }
    public void setMeanIntercept(BigDecimal value) { meanIntercept = value; }
    private CurveSegment segment(BigDecimal x1, BigDecimal y1, BigDecimal x2, BigDecimal y2) {
        return new CurveSegment(new CurvePoint(x1, y1), new CurvePoint(x2, y2));
    }
}
