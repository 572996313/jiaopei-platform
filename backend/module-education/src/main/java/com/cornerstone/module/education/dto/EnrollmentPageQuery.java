package com.cornerstone.module.education.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 报名记录分页查询参数,GET /api/education/enrollments/page 使用。
 */
@Schema(description = "报名记录分页查询参数")
public class EnrollmentPageQuery extends PageQuery {

    @Schema(description = "机构 ID,精确匹配")
    private Long institutionId;

    @Schema(description = "班级 ID,精确匹配")
    private Long classId;

    @Schema(description = "学员 ID,精确匹配")
    private Long studentId;

    public Long getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(Long institutionId) {
        this.institutionId = institutionId;
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
}
