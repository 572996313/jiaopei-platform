package com.cornerstone.module.education.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 教师分页查询参数,GET /api/education/teachers/page 使用。
 */
@Schema(description = "教师分页查询参数")
public class TeacherPageQuery extends PageQuery {

    @Schema(description = "机构 ID,精确匹配")
    private Long institutionId;

    @Schema(description = "校区 ID,精确匹配")
    private Long campusId;

    @Schema(description = "关键字,模糊匹配姓名/工号/手机号")
    private String keyword;

    @Schema(description = "状态(active=在职,suspended=暂停,left=离职)",
            allowableValues = {"active", "suspended", "left"})
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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
