package com.cornerstone.module.organization.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "时段模板视图")
public record TimeSlotTemplateView(
    @Schema(description = "主键ID") Long id,
    @Schema(description = "机构ID") Long institutionId,
    @Schema(description = "校区ID,空表示机构级模板") Long campusId,
    @Schema(description = "模板名称") String templateName,
    @Schema(description = "节次参数快照") JsonNode config,
    @Schema(description = "状态(active=启用,inactive=停用)") String status,
    @Schema(description = "备注") String remark,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "更新时间") LocalDateTime updatedAt
) {
}
