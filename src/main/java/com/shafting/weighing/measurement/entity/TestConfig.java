package com.shafting.weighing.measurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;

import com.baomidou.mybatisplus.annotation.TableField;

import com.baomidou.mybatisplus.annotation.TableId;

import com.baomidou.mybatisplus.annotation.TableLogic;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("test_config")
/** 一套可供测量任务引用的六点参数方案。 */
public class TestConfig {

    /** 参数方案主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 参数方案名称。 */
    @TableField("config_name")
    private String configName;

    /** 每次完整保存后递增的版本号。 */
    @TableField("config_version")
    private Integer configVersion;

    /** 方案状态，例如 DRAFT。 */
    private String status;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public Integer getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Integer configVersion) {
        this.configVersion = configVersion;
    }

    public String getStatus() {
        return status;
    }
}
