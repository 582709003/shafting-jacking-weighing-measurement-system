package com.shafting.weighing.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;

import com.baomidou.mybatisplus.annotation.TableField;

import com.baomidou.mybatisplus.annotation.TableId;

import com.baomidou.mybatisplus.annotation.TableLogic;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_user")
/** 系统登录用户。 */
public class SysUser {

    /** 用户主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 唯一登录账号。 */
    private String username;

    /** BCrypt 密码摘要，不保存明文密码。 */
    @TableField("password_hash")
    private String passwordHash;

    /** 页面显示名称。 */
    @TableField("display_name")
    private String displayName;

    /** 账号状态：1 启用，0 停用。 */
    private Integer status;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
