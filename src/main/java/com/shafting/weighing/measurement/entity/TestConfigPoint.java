package com.shafting.weighing.measurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@TableName("test_config_point")
/** 参数方案中某一个测量点的运行和计算参数。 */
public class TestConfigPoint {

    /** 方案点参数主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属参数方案主键。 */
    @TableField("config_id")
    private Long configId;

    /** 所属测量点主键。 */
    @TableField("point_id")
    private Long pointId;

    /** 标准顶升力，单位 kN。 */
    @TableField("standard_force")
    private BigDecimal standardForce;

    /** 当前通道总值，单位 kN；仅记录，不参与测量结果计算。 */
    @TableField("total_value")
    private BigDecimal totalValue;

    /** 工作页手动上升保存的目标位移，单位 mm；0 表示未设置。 */
    @TableField("manual_target_displacement")
    private BigDecimal manualTargetDisplacement;

    /** 将平均截距换算为测量值的顶升系数。 */
    @TableField("jacking_coefficient")
    private BigDecimal jackingCoefficient;

    /** 自动测试目标位移兼安全上限，单位 mm。 */
    @TableField("max_displacement")
    private BigDecimal maxDisplacement;

    /** 电机转速，单位 r/min。 */
    @TableField("motor_rpm")
    private Integer motorRpm;

    /** 自动顶升压力间隔，单位 MPa。 */
    @TableField("pressure_interval_mpa")
    private BigDecimal pressureIntervalMpa;

    /** 到达目标后的保持时间，单位秒。 */
    @TableField("hold_seconds")
    private Integer holdSeconds;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
    }

    public BigDecimal getStandardForce() {
        return standardForce;
    }

    public void setStandardForce(BigDecimal standardForce) {
        this.standardForce = standardForce;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public BigDecimal getManualTargetDisplacement() {
        return manualTargetDisplacement;
    }

    public void setManualTargetDisplacement(BigDecimal manualTargetDisplacement) {
        this.manualTargetDisplacement = manualTargetDisplacement;
    }

    public BigDecimal getJackingCoefficient() {
        return jackingCoefficient;
    }

    public void setJackingCoefficient(BigDecimal jackingCoefficient) {
        this.jackingCoefficient = jackingCoefficient;
    }

    public BigDecimal getMaxDisplacement() {
        return maxDisplacement;
    }

    public void setMaxDisplacement(BigDecimal maxDisplacement) {
        this.maxDisplacement = maxDisplacement;
    }

    public Integer getMotorRpm() {
        return motorRpm;
    }

    public void setMotorRpm(Integer motorRpm) {
        this.motorRpm = motorRpm;
    }

    public BigDecimal getPressureIntervalMpa() {
        return pressureIntervalMpa;
    }

    public void setPressureIntervalMpa(BigDecimal pressureIntervalMpa) {
        this.pressureIntervalMpa = pressureIntervalMpa;
    }

    public Integer getHoldSeconds() {
        return holdSeconds;
    }

    public void setHoldSeconds(Integer holdSeconds) {
        this.holdSeconds = holdSeconds;
    }
}
