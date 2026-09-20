package com.cornerstone.module.education.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 课程分页查询参数,GET /api/education/courses/page 使用。
 */
@Schema(description = "课程分页查询参数")
public class CoursePageQuery extends PageQuery {

    @Schema(description = "机构 ID,精确匹配")
    private Long institutionId;

    @Schema(description = "课程分类 ID,精确匹配")
    private Long categoryId;

    @Schema(description = "关键字,模糊匹配课程名称/编号/学科")
    private String keyword;

    @Schema(description = "状态(active=启用,inactive=停用)", allowableValues = {"active", "inactive"})
    private String status;

    public Long getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(Long institutionId) {
        this.institutionId = institutionId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
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
