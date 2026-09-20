package com.jiaopei.platform.organization.service.impl;

import com.jiaopei.platform.config.properties.JiaopeiProperties;
import com.jiaopei.platform.organization.dto.OrganizationBootstrapView;
import com.jiaopei.platform.organization.dto.OrganizationSchemaView;
import com.jiaopei.platform.organization.service.OrganizationApplicationService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrganizationApplicationServiceImpl implements OrganizationApplicationService {

    private final JiaopeiProperties properties;

    public OrganizationApplicationServiceImpl(JiaopeiProperties properties) {
        this.properties = properties;
    }

    @Override
    public OrganizationBootstrapView getBootstrapView() {
        return new OrganizationBootstrapView(
            properties.getModules().isOrganizationEnabled(),
            "就绪",
            List.of("organization", "campus", "classroom", "term"),
            List.of(
                "在 organization.domain 下补充聚合根",
                "在 organization.repository 下实现仓储",
                "用权限注解暴露管理接口"
            )
        );
    }

    @Override
    public OrganizationSchemaView getSchemaView() {
        return new OrganizationSchemaView(
            List.of("jp_institution", "jp_campus", "jp_classroom", "jp_term", "jp_time_slot"),
            List.of("institution", "campus", "classroom", "term", "timeSlot"),
            "V2__create_organization_core_tables.sql"
        );
    }
}
