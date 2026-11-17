package com.shafting.weighing.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

@TableName("measurement_sample")
/**
 * 一次设备采样的原始记录，同时作为曲线页采样接口的响应元素。
 *
 * <p>人工曲线修订只新增拟合版本，不会修改该表记录。</p>
 */
public class MeasurementSample {

    /** 采样主键，仅用于数据库内部标识。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属测量运行主键。 */
    @TableField("run_id")
    private Long runId;
    /** 所属测量点主键。 */
    @TableField("point_id")
    private Long pointId;
    /** 采样时的自动流程阶段。 */
    private String stage;
    /** 运行内单调递增的采样序号。 */
    @TableField("sequence_no")
    private Long sequenceNo;
    /** 采集时间。 */
    @TableField("captured_at")
    private Instant capturedAt;
    /** 压力，单位 MPa。 */
    @TableField("pressure_mpa")
    private BigDecimal pressureMpa;
    /** 顶升力，单位 kN。 */
    @TableField("force_kn")
    private BigDecimal forceKn;
    /** 位移，单位 mm。 */
    @TableField("displacement_mm")
    private BigDecimal displacementMm;
    /** 设备原始报文；模拟器记录数据来源标识。 */
    @TableField("raw_payload_json")
    private String rawPayloadJson;
    /** 数据有效标记：1 有效，0 无效。 */
    private Integer valid;

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getPointId() { return pointId; }
    public void setPointId(Long pointId) { this.pointId = pointId; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public Long getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Long sequenceNo) { this.sequenceNo = sequenceNo; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
    public BigDecimal getPressureMpa() { return pressureMpa; }
    public void setPressureMpa(BigDecimal pressureMpa) { this.pressureMpa = pressureMpa; }
    public BigDecimal getForceKn() { return forceKn; }
    public void setForceKn(BigDecimal forceKn) { this.forceKn = forceKn; }
    public BigDecimal getDisplacementMm() { return displacementMm; }
    public void setDisplacementMm(BigDecimal displacementMm) { this.displacementMm = displacementMm; }
    public String getRawPayloadJson() { return rawPayloadJson; }
    public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
    public Integer getValid() { return valid; }
    public void setValid(Integer valid) { this.valid = valid; }
}
