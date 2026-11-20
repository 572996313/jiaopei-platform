package com.cornerstone.module.education.fee.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 收费台账分页查询参数，GET /api/education/fee-ledgers/page 使用。
 */
@Schema(description = "收费台账分页查询参数")
public class FeeLedgerPageQuery extends PageQuery {

    @Schema(description = "机构 ID,精确匹配")
    private Long institutionId;

    @Schema(description = "校区 ID,精确匹配")
    private Long campusId;

    @Schema(description = "学期 ID,精确匹配")
    private Long termId;

    @Schema(description = "班级 ID,精确匹配")
    private Long classId;

    @Schema(description = "学员 ID,精确匹配")
    private Long studentId;

    @Schema(description = "状态(active=在收,settled=已结清)", allowableValues = {"active", "settled"})
    private String status;

    @Schema(description = "关键字,模糊匹配学号/姓名/班级名")
    private String keyword;

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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
