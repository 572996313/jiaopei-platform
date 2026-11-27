package com.cornerstone.module.education.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "自动排课批次撤销结果")
public record ScheduleAutoRollbackResponse(
    @Schema(description = "批次ID") Long batchId,
    @Schema(description = "本批原本生成的课次数") Integer generatedCount,
    @Schema(description = "本次撤掉（连同授课关系一起删除）的课次数") Integer canceledCount,
    @Schema(description = "因已开课或已到上课日而保留的课次数") Integer keptCount,
    @Schema(description = "给操作员的一句话说明，含保留课次的处置指引") String message
) {
}
