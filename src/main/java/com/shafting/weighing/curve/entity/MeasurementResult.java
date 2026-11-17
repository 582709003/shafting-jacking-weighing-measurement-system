package com.shafting.weighing.curve.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("measurement_result")
/** 一次测量运行当前采用的计算结果。 */
public class MeasurementResult {
    /** 结果主键。 */
    @TableId(type = IdType.AUTO) private Long id;
    /** 所属测量运行主键，一次运行最多一条当前结果。 */
    @TableField("run_id") private Long runId;
    /** 参数方案标准力，单位 kN。 */
    @TableField("standard_force_kn") private BigDecimal standardForceKn;
    /** 计算后的测量值，单位 kN。 */
    @TableField("measured_force_kn") private BigDecimal measuredForceKn;
    /** 测量值减标准值，单位 kN。 */
    @TableField("deviation_kn") private BigDecimal deviationKn;
    /** 偏差百分比，标准值为 0 时为空。 */
    @TableField("deviation_rate") private BigDecimal deviationRate;
    /** 计算测量值时使用的顶升系数。 */
    @TableField("adjustment_coefficient") private BigDecimal adjustmentCoefficient;
    /** 调整量；公式未确认前为空。 */
    @TableField("adjustment_amount_mm") private BigDecimal adjustmentAmountMm;
    /** 当前结果对应的曲线修订号。 */
    @TableField("revision_no") private Integer revisionNo;
    /** 结果状态：DRAFT 待保存，SAVED 已保存。 */
    @TableField("result_status") private String resultStatus;
    /** 执行保存确认的用户主键；DRAFT 时为空。 */
    @TableField("saved_by") private Long savedBy;
    /** 保存确认时间；DRAFT 时为空。 */
    @TableField("saved_at") private LocalDateTime savedAt;
    /** 逻辑删除标记：0 有效，1 已删除。 */
    @TableLogic private Integer deleted;

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long value) { runId = value; }
    public BigDecimal getStandardForceKn() { return standardForceKn; }
    public void setStandardForceKn(BigDecimal value) { standardForceKn = value; }
    public BigDecimal getMeasuredForceKn() { return measuredForceKn; }
    public void setMeasuredForceKn(BigDecimal value) { measuredForceKn = value; }
    public BigDecimal getDeviationKn() { return deviationKn; }
    public void setDeviationKn(BigDecimal value) { deviationKn = value; }
    public BigDecimal getDeviationRate() { return deviationRate; }
    public void setDeviationRate(BigDecimal value) { deviationRate = value; }
    public BigDecimal getAdjustmentCoefficient() { return adjustmentCoefficient; }
    public void setAdjustmentCoefficient(BigDecimal value) { adjustmentCoefficient = value; }
    public BigDecimal getAdjustmentAmountMm() { return adjustmentAmountMm; }
    public void setAdjustmentAmountMm(BigDecimal value) { adjustmentAmountMm = value; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer value) { revisionNo = value; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String value) { resultStatus = value; }
    public Long getSavedBy() { return savedBy; }
    public void setSavedBy(Long value) { savedBy = value; }
    public LocalDateTime getSavedAt() { return savedAt; }
    public void setSavedAt(LocalDateTime value) { savedAt = value; }
}
