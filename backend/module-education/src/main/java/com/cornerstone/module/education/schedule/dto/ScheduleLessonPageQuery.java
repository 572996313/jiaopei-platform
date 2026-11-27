package com.cornerstone.module.education.schedule.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 排课课次分页查询参数,GET /api/education/schedules/lessons/page 与
 * GET /api/education/schedules/lessons/pending-makeup-page 使用。
 */
@Schema(description = "排课课次分页查询参数")
public class ScheduleLessonPageQuery extends PageQuery {

    @Schema(description = "机构 ID,精确匹配")
    private Long institutionId;

    @Schema(description = "校区 ID,精确匹配")
    private Long campusId;

    @Schema(description = "班级 ID,精确匹配")
    private Long classId;

    @Schema(description = "来源排课计划 ID,精确匹配")
    private Long planId;

    @Schema(description = "上课日期,精确匹配")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate lessonDate;

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

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public LocalDate getLessonDate() {
        return lessonDate;
    }

    public void setLessonDate(LocalDate lessonDate) {
        this.lessonDate = lessonDate;
    }
}
