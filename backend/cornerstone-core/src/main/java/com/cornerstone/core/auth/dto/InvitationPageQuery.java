package com.cornerstone.core.auth.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "注册凭证分页查询参数")
public class InvitationPageQuery extends PageQuery {

    @Schema(description = "关键字,模糊匹配凭证码/备注")
    private String keyword;

    @Schema(description = "凭证类型(INVITE=邀请注册,ACTIVATE=激活预建账号)", allowableValues = {"INVITE", "ACTIVATE"})
    private String kind;

    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    private String status;

    @Schema(description = "机构ID,精确匹配")
    private Long institutionId;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
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
