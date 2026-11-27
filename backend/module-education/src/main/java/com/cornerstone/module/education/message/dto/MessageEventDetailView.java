package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "事件详情(含时间线)")
public record MessageEventDetailView(
    @Schema(description = "事件主体") MessageEventView event,
    @Schema(description = "处理时间线(人工事项用，系统事件通常为空)") List<MessageTimelineView> timeline
) {
}
