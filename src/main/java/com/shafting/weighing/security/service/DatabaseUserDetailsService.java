package com.shafting.weighing.security.service;

import com.shafting.weighing.security.entity.SysUser;
import com.shafting.weighing.security.mapper.SysUserMapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
/** 从系统用户、用户角色表加载 Spring Security 登录信息。 */
public class DatabaseUserDetailsService implements UserDetailsService {

    private static final int ENABLED = 1;

    private final SysUserMapper sysUserMapper;

    public DatabaseUserDetailsService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    /**
     * 按用户名加载启用用户及其角色；用户不存在或停用时拒绝登录。
     */
    public UserDetails loadUserByUsername(String username) {
        String normalizedUsername = username.strip().toLowerCase(Locale.ROOT);
        SysUser sysUser = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, normalizedUsername));
        if (sysUser == null || !Integer.valueOf(ENABLED).equals(sysUser.getStatus())) {
            throw new UsernameNotFoundException("用户不存在或已停用");
        }
        List<String> roleCodes = sysUserMapper.selectRoleCodes(sysUser.getId());
        return User.withUsername(sysUser.getUsername())
                .password(sysUser.getPasswordHash())
                .roles(roleCodes.toArray(String[]::new))
                .build();
    }
}
