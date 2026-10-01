package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 考勤汇总查询参数,GET /api/education/attendance/summary 使用。
 *
 * <p>区间必填且为自然日期边界(不做整周对齐),最长 366 天;按班级或按学员两种分组共用一套取数。
 */
@Schema(description = "考勤汇总查询参数")
public class AttendanceSummaryQuery {

    @Schema(description = "机构 ID,仅作筛选;服务端会与登录用户所属机构比对,不一致直接拒绝")
    private Long institutionId;

    @NotNull(message = "统计区间起始日期不能为空")
    @Schema(description = "统计区间起始日期(YYYY-MM-DD)")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @NotNull(message = "统计区间截止日期不能为空")
    @Schema(description = "统计区间截止日期(YYYY-MM-DD)")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;

    @Schema(description = "校区 ID,精确匹配")
    private Long campusId;

    @Schema(description = "班级 ID,精确匹配")
    private Long classId;

    @Schema(description = "学员 ID,仅按学员分组时生效;传了只统计该学员")
    private Long studentId;

    @Schema(description = "分组维度:class=按班级(默认),student=按学员", allowableValues = {"class", "student"})
    private String groupBy = "class";

    public Long getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(Long institutionId) {
        this.institutionId = institutionId;
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

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }
}
