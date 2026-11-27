package com.cornerstone.module.education.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "清理已删除课次的结果")
public record TimeSlotLessonCleanupResponse(
    @Schema(description = "本次物理删除的已删除课次数")
    long purgedCount,
    @Schema(description = "跳过的已删除课次数:还有未删除的补课课次指着它,删了会让补课记录悬空",
            requiredMode = Schema.RequiredMode.REQUIRED)
    long skippedCount,
    @Schema(description = "清理后仍指着本节次的未删除课次数,为 0 表示节次可以直接删除")
    long remainingLiveRefCount
) {
}
