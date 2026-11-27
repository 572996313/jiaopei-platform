package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "事件处理时间线")
public record MessageTimelineView(
    @Schema(description = "时间线ID") Long id,
    @Schema(description = "动作(create/comment/assign/status/backfill)") String action,
    @Schema(description = "内容") String content,
    @Schema(description = "操作人账号ID") Long userId,
    @Schema(description = "操作人姓名") String userName,
    @Schema(description = "操作时间") LocalDateTime createdAt
) {
}
