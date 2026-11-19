package com.shafting.weighing.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 系统可分配角色，直接用于用户管理页面的角色选项。 */
@TableName("sys_role")
public class SysRole {

    /** 角色主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 稳定角色编码：ADMIN、ENGINEER、OPERATOR 或 VIEWER。 */
    @TableField("role_code")
    private String roleCode;

    /** 页面显示的中文角色名称。 */
    @TableField("role_name")
    private String roleName;

    public Long getId() {
        return id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRoleName() {
        return roleName;
    }
}
