package com.cornerstone.module.education.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

/**
 * 排课计划分页查询参数,GET /api/education/schedule-plans/page 使用。
 */
@Schema(description = "排课计划分页查询参数")
public class SchedulePlanPageQuery extends PageQuery {

    @Schema(description = "机构 ID,精确匹配")
    private Long institutionId;

    @Schema(description = "校区 ID,精确匹配")
    private Long campusId;

    @Schema(description = "班级 ID,精确匹配")
    private Long classId;

    @Schema(description = "计划状态(active=启用,inactive=停用),精确匹配")
    private String status;

    @Schema(description = "生效日期区间起(与计划截止日期比较),可选")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate activeFrom;

    @Schema(description = "生效日期区间止(与计划起始日期比较),可选")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate activeTo;

    public Long getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(Long institutionId) {
        this.institutionId = institutionId;
    }

    public Long getCampusId() {
        return campusId;
    }

    public void setCampusId(Long campusId) {
        this.campusId = campusId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getActiveFrom() {
        return activeFrom;
    }

    public void setActiveFrom(LocalDate activeFrom) {
        this.activeFrom = activeFrom;
    }

    public LocalDate getActiveTo() {
        return activeTo;
    }

    public void setActiveTo(LocalDate activeTo) {
        this.activeTo = activeTo;
    }
}
