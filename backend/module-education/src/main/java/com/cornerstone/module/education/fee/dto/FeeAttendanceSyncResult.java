package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "出勤同步结果")
public record FeeAttendanceSyncResult(
    @Schema(description = "涉及的台账数")
    int ledgerCount,
    @Schema(description = "刷新的月份行数")
    int syncedCount,
    @Schema(description = "因手工覆盖而保留原值的行数")
    int keptOverrideCount
) {
}
