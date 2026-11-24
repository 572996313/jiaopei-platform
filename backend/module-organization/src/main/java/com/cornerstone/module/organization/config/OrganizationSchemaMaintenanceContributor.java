package com.cornerstone.module.organization.config;

import com.cornerstone.core.system.schema.SchemaMaintenanceContributor;
import com.cornerstone.core.system.schema.SchemaMaintenanceEntry;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 组织主数据表的维护入口登记：数据字典页据此显示"这批数据去哪个页面改"。
 *
 * <p>随模块开关一起注册/消失——模块关闭时字典页自然少掉这几条入口。</p>
 */
@Component("organizationSchemaMaintenanceContributor")
public class OrganizationSchemaMaintenanceContributor implements SchemaMaintenanceContributor {

    @Override
    public String moduleKey() {
        return "organization";
    }

    @Override
    public List<SchemaMaintenanceEntry> entries() {
        return List.of(
            new SchemaMaintenanceEntry("jp_campus", "校区管理", "/campus",
                "校区档案；学期列表也在这一页维护"),
            new SchemaMaintenanceEntry("jp_term", "校区管理", "/campus",
                "学期是排课的周期维度，在「校区与学期」页下半段维护"),
            new SchemaMaintenanceEntry("jp_classroom", "教室管理", "/classrooms",
                "教室档案，排课时按所属校区占用"),
            new SchemaMaintenanceEntry("jp_time_slot_template", "时间段模板", "/time-slots",
                "按校区维护的节次模板"),
            new SchemaMaintenanceEntry("jp_time_slot", "节次明细", "/time-slot-list",
                "模板展开后的具体节次，排课按它落课"),
            new SchemaMaintenanceEntry("jp_user_campus_pref", "校区默认账号", "/campus-accounts",
                "账号的默认校区偏好（按校区成组维护）")
        );
    }
}
