package com.cornerstone.module.organization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "时段模板更新请求(字段可选,仅更新传入项)")
public record TimeSlotTemplateUpdateRequest(
    @Schema(description = "模板名称")
    @JsonAlias({"name", "template_name"})
    String templateName,
    @Schema(description = "节次参数快照")
    @JsonAlias("config_json")
    JsonNode config,
    @Schema(description = "状态(active=启用,inactive=停用)")
    String status,
    @Schema(description = "备注")
    String remark
) {
}
