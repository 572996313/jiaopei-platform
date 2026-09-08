package com.jiaopei.platform.system.dto;

import com.jiaopei.platform.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 系统参数分页查询参数,GET /api/system/config/page 使用。
 */
@Schema(description = "系统参数分页查询参数")
public class SystemConfigPageQuery extends PageQuery {

    @Schema(description = "关键字,模糊匹配配置键/显示名称/配置说明")
    private String keyword;

    @Schema(description = "配置分组,精确匹配(general=通用,auth=认证,system=系统,business=业务)",
            allowableValues = {"general", "auth", "system", "business"})
    private String configGroup;

    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    private String status;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getConfigGroup() {
        return configGroup;
    }

    public void setConfigGroup(String configGroup) {
        this.configGroup = configGroup;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
