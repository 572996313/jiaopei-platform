package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "事件阅读人")
public record MessageReaderView(
    @Schema(description = "账号ID") Long userId,
    @Schema(description = "账号姓名") String userName,
    @Schema(description = "首次已读时间") LocalDateTime readAt
) {
}
