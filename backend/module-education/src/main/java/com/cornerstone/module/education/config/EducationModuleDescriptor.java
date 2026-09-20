package com.cornerstone.module.education.config;

import com.cornerstone.core.config.properties.CornerstoneProperties;
import com.cornerstone.core.system.module.ModuleDescriptor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 教务模块自述信息。
 */
@Component
@Order(30)
public class EducationModuleDescriptor implements ModuleDescriptor {

    private final CornerstoneProperties properties;

    public EducationModuleDescriptor(CornerstoneProperties properties) {
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
