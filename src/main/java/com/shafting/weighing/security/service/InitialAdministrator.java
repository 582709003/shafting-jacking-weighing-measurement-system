package com.shafting.weighing.security.service;

import com.shafting.weighing.security.entity.SysUser;
import com.shafting.weighing.security.mapper.SysUserMapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
/** 首次启动时确保系统存在 admin 管理员账号。 */
public class InitialAdministrator implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final String initialPassword;

    public InitialAdministrator(SysUserMapper sysUserMapper,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.bootstrap.admin-password:Admin@123}") String initialPassword) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.initialPassword = initialPassword;
    }

    @Override
    @Transactional
    /**
     * 仅在 admin 不存在时创建账号并绑定管理员角色，后续启动不会重置密码。
     */
    public void run(ApplicationArguments args) {
        Long existingId = sysUserMapper.selectIdByUsername("admin");
        if (existingId != null) {
            return;
        }
        SysUser administrator = new SysUser();
        administrator.setUsername("admin");
        administrator.setPasswordHash(passwordEncoder.encode(initialPassword));
        administrator.setDisplayName("系统管理员");
        administrator.setStatus(1);
        sysUserMapper.insert(administrator);
        sysUserMapper.insertAdministratorRole(administrator.getId());
    }
}
