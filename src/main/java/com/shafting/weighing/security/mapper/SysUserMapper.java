package com.shafting.weighing.security.mapper;

import com.shafting.weighing.security.entity.SysUser;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
/** 用户和角色关系的数据访问接口。 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 查询用户拥有的角色编码，供 Spring Security 构建权限。 */
    @Select("""
            SELECT r.role_code
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id
            """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    /** 按登录账号查询有效用户主键，审计和任务创建使用。 */
    @Select("SELECT id FROM sys_user WHERE username = #{username} AND deleted = 0")
    Long selectIdByUsername(@Param("username") String username);

    /** 为首次启动创建的 admin 账号绑定 ADMIN 角色。 */
    @Insert("""
            INSERT INTO sys_user_role (user_id, role_id)
            SELECT #{userId}, id FROM sys_role WHERE role_code = 'ADMIN'
            """)
    int insertAdministratorRole(@Param("userId") Long userId);
}
