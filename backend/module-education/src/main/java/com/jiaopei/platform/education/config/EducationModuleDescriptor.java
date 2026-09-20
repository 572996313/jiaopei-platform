package com.jiaopei.platform.education.config;

import com.jiaopei.platform.config.properties.JiaopeiProperties;
import com.jiaopei.platform.system.module.ModuleDescriptor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 教务模块自述信息。
 */
@Component
@Order(30)
public class EducationModuleDescriptor implements ModuleDescriptor {

    private final JiaopeiProperties properties;

    public EducationModuleDescriptor(JiaopeiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return "education";
    }

    @Override
    public String name() {
        return "教务";
    }

    @Override
    public boolean enabled() {
        return properties.getModules().isEducationEnabled();
    }

    @Override
    public String status() {
        return "预留";
    }
}
