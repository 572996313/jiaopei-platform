package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "推荐订阅(只推荐，不自动生效)")
public record MessageRecommendationView(
    @Schema(description = "维度") String ruleType,
    @Schema(description = "维度值") String ruleValue,
    @Schema(description = "展示名") String label,
    @Schema(description = "推荐原因") String reason,
    @Schema(description = "是否已订阅") boolean subscribed
) {
}
