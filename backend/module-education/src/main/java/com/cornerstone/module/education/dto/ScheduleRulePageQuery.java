package com.cornerstone.module.education.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 排课规则分页查询参数,GET /api/education/schedule-rules/page 使用。
 */
@Schema(description = "排课规则分页查询参数")
public class ScheduleRulePageQuery extends PageQuery {

    @Schema(description = "机构 ID,精确匹配")
    private Long institutionId;

    @Schema(description = "规则类型(forbidden=禁排,required=必排),精确匹配")
    private String ruleType;

    @Schema(description = "规则对象类型(class=班级,teacher=教师,classroom=教室),精确匹配")
    private String targetType;

    @Schema(description = "规则对象 ID,精确匹配")
    private Long targetId;

    @Schema(description = "规则状态(active=启用,inactive=停用),精确匹配")
    private String status;

    public Long getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(Long institutionId) {
        this.institutionId = institutionId;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
