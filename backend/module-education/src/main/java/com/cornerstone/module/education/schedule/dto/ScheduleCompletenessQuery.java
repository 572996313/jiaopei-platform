package com.cornerstone.module.education.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 排课完成度报表查询参数,GET /api/education/schedule-completeness 使用。
 *
 * <p>起止日期都可省略：给了 termId 就按该学期区间算，都没给则从今天所在自然周起算 12 周。
 */
@Schema(description = "排课完成度报表查询参数")
public class ScheduleCompletenessQuery {

    @Schema(description = "机构 ID,仅作筛选;服务端会与登录用户所属机构比对,不一致直接拒绝")
    private Long institutionId;

    @Schema(description = "校区 ID,精确匹配")
    private Long campusId;

    @Schema(description = "学期 ID,精确匹配(同时决定未指定日期时的统计区间)")
    private Long termId;

    @Schema(description = "班级 ID,精确匹配")
    private Long classId;

    @Schema(description = "统计区间起始日期(YYYY-MM-DD),可选;按自然周向前对齐到周一")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @Schema(description = "统计区间截止日期(YYYY-MM-DD),可选;按自然周向后对齐到周日")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;

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

    public Long getTermId() {
        return termId;
    }

    public void setTermId(Long termId) {
        this.termId = termId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }
}
