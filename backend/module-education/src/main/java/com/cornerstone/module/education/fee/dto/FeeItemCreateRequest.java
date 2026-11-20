package com.cornerstone.module.education.fee.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "收费项目创建请求")
public record FeeItemCreateRequest(
    @Schema(description = "机构ID(多租户隔离,留空取登录机构)")
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long institutionId,
    @Schema(description = "收费项目编码(可留空,留空时按编号规则自动生成)")
    @JsonAlias({"code", "item_code"})
    String itemCode,
    @Schema(description = "收费项目名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "item_name"})
    String itemName,
    @Schema(description = "计费方式(monthly=按月,daily=按天)", allowableValues = {"monthly", "daily"})
    @JsonAlias("billing_mode")
    String billingMode,
    @Schema(description = "排序号(决定导出矩阵块内的项目行序)")
    @JsonAlias("sort_no")
    Integer sortNo,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
