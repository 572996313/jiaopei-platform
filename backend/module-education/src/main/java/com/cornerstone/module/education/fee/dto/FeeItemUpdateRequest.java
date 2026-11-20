package com.cornerstone.module.education.fee.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "收费项目更新请求")
public record FeeItemUpdateRequest(
    @Schema(description = "收费项目编码")
    @JsonAlias({"code", "item_code"})
    String itemCode,
    @Schema(description = "收费项目名称")
    @JsonAlias({"name", "item_name"})
    String itemName,
    @Schema(description = "计费方式(monthly=按月,daily=按天)", allowableValues = {"monthly", "daily"})
    @JsonAlias("billing_mode")
    String billingMode,
    @Schema(description = "排序号")
    @JsonAlias("sort_no")
    Integer sortNo,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    String status,
    @Schema(description = "备注")
    String remark
) {
}
