package com.shafting.weighing.measurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;

import com.baomidou.mybatisplus.annotation.TableField;

import com.baomidou.mybatisplus.annotation.TableId;

import com.baomidou.mybatisplus.annotation.TableLogic;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("measurement_point")
/** 六个逻辑测量点之一，不包含尚未确认的 PLC 物理地址。 */
public class MeasurementPoint {

    /** 测量点主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 程序使用的稳定编码。 */
    @TableField("point_code")
    private String pointCode;

    /** 页面显示名称。 */
    @TableField("point_name")
    private String pointName;

    /** SHAFTING 或 MAIN_ENGINE。 */
    @TableField("point_type")
    private String pointType;

    /** 页面排列顺序。 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 是否允许页面选择：1 启用，0 停用。 */
    private Integer enabled;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public String getPointCode() {
        return pointCode;
    }

    public String getPointName() {
        return pointName;
    }

    public String getPointType() {
        return pointType;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public Integer getEnabled() {
        return enabled;
    }
}
