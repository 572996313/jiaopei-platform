package com.cornerstone.module.organization.service.impl;

import com.cornerstone.core.config.properties.CornerstoneProperties;
import com.cornerstone.module.organization.dto.OrganizationBootstrapView;
import com.cornerstone.module.organization.dto.OrganizationSchemaView;
import com.cornerstone.module.organization.service.OrganizationApplicationService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrganizationApplicationServiceImpl implements OrganizationApplicationService {

    private final CornerstoneProperties properties;

    public OrganizationApplicationServiceImpl(CornerstoneProperties properties) {
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
        // 机构（sys_institution）阶段二A 已迁回底座 tenant 包，不再列进本模块的表清单
        return new OrganizationSchemaView(
            List.of("jp_campus", "jp_classroom", "jp_term", "jp_time_slot"),
            List.of("campus", "classroom", "term", "timeSlot"),
            "V2__create_organization_core_tables.sql"
        );
    }
}
