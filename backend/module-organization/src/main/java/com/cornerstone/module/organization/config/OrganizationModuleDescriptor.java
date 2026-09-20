package com.cornerstone.module.organization.config;

import com.cornerstone.core.config.properties.JiaopeiProperties;
import com.cornerstone.core.system.module.ModuleDescriptor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 组织主数据模块自述信息。
 */
@Component
@Order(20)
public class OrganizationModuleDescriptor implements ModuleDescriptor {

    private final JiaopeiProperties properties;

    public OrganizationModuleDescriptor(JiaopeiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return "organization";
    }

    @Override
    public String name() {
        return "组织主数据";
    }

    @Override
    public boolean enabled() {
        return properties.getModules().isOrganizationEnabled();
    }

    @Override
    public String status() {
        return "就绪";
    }
}
