package com.jiaopei.platform.education.dto;

import com.jiaopei.platform.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 学员分页查询参数,GET /api/education/students/page 使用。
 */
@Schema(description = "学员分页查询参数")
public class StudentPageQuery extends PageQuery {

    @Schema(description = "机构 ID,精确匹配")
    private Long institutionId;

    @Schema(description = "校区 ID,精确匹配")
    private Long campusId;

    @Schema(description = "关键字,模糊匹配学号/姓名")
    private String keyword;

    @Schema(description = "状态(studying=在读,suspended=停课,graduated=毕业,withdrawn=退学)",
            allowableValues = {"studying", "suspended", "graduated", "withdrawn"})
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
