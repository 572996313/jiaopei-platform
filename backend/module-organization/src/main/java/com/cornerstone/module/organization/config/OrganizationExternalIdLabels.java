package com.cornerstone.module.organization.config;

import com.cornerstone.core.common.web.ExternalIdFieldLabelContributor;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 组织主数据模块的字段中文名，供底座拼报错文案。
 *
 * <p>随模块开关一起装配：关闭 organization 后这些词不进词表，报错回落成原始字段名。</p>
 */
@Component
public class OrganizationExternalIdLabels implements ExternalIdFieldLabelContributor {

    @Override
    public Map<String, String> labels() {
        return Map.of(
            "campusId", "校区ID",
            "classroomId", "教室ID",
            "termId", "学期ID",
            "timeSlotId", "时段ID",
            "templateId", "时间段模板ID");
    }
}
