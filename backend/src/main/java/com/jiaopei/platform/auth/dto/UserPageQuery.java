package com.jiaopei.platform.auth.dto;

import com.jiaopei.platform.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户分页查询参数,GET /api/system/users/page 使用。
 */
@Schema(description = "用户分页查询参数")
public class UserPageQuery extends PageQuery {

    @Schema(description = "关键字,模糊匹配账号/显示名称/手机号")
    private String keyword;

    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    private String status;

    @Schema(description = "机构 ID,精确匹配")
    private Long institutionId;

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

    public Long getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(Long institutionId) {
        this.institutionId = institutionId;
    }
}
