package com.cornerstone.module.education.teachingclass.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 班级学员关系分页查询参数,GET /api/education/class-students/page 使用。
 */
@Schema(description = "班级学员关系分页查询参数")
public class ClassStudentPageQuery extends PageQuery {

    @Schema(description = "机构 ID,精确匹配")
    private Long institutionId;

    @Schema(description = "校区 ID,按班级所属校区筛选")
    private Long campusId;

    @Schema(description = "班级 ID,精确匹配")
    private Long classId;

    @Schema(description = "学员 ID,精确匹配")
    private Long studentId;

    @Schema(description = "状态(active=在读,quit=退班,graduated=结业,reserved=预占)")
    private String status;

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

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
