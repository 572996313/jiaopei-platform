package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "排课计划提交响应")
public record SchedulePlanCommitResponse(
    @Schema(description = "计划ID")
    Long planId,
    @Schema(description = "成功生成的课次数")
    int createdCount,
    @Schema(description = "跳过的勾选项数(该键课次已存在,幂等补排)")
    int skippedCount,
    @Schema(description = "跳过明细说明列表")
    List<String> skipMessages
) {
}
