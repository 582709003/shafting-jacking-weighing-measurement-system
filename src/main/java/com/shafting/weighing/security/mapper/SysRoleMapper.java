package com.shafting.weighing.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shafting.weighing.security.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/** 用户管理页面读取系统角色选项的数据访问接口。 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
