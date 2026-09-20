package com.cornerstone.core.auth.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色分页查询参数,GET /api/system/roles/page 使用。
 */
@Schema(description = "角色分页查询参数")
public class RolePageQuery extends PageQuery {

    @Schema(description = "关键字,模糊匹配角色编码/角色名称")
    private String keyword;

    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    private String status;

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
