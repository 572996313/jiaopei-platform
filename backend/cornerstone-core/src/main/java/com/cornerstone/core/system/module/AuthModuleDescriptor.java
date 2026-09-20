package com.cornerstone.core.system.module;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 底座固定能力的自述：鉴权/RBAC/菜单字典/系统参数/机构租户/审计，常驻不可关闭。
 */
@Component
@Order(10)
public class AuthModuleDescriptor implements ModuleDescriptor {

    @Override
    public String code() {
        return "auth";
    }

    @Override
    public String name() {
        return "权限认证";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String status() {
        return "就绪";
    }
}
