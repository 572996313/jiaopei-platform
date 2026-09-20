package com.jiaopei.platform.education.dto;

import com.jiaopei.platform.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 家长联系人分页查询参数,GET /api/education/student-parents/page 使用。
 */
@Schema(description = "家长联系人分页查询参数")
public class StudentParentPageQuery extends PageQuery {

    @Schema(description = "机构 ID,精确匹配")
    private Long institutionId;

    @Schema(description = "学员 ID,精确匹配")
    private Long studentId;

    public Long getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(Long institutionId) {
        this.institutionId = institutionId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }
}
