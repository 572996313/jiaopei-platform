package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "批量建账结果")
public record FeeLedgerGenerateResult(
    @Schema(description = "新建台账数")
    int createdCount,
    @Schema(description = "跳过数(已有台账的学员)")
    int skippedCount,
    @Schema(description = "每个台账自动建的收费项目行数")
    int itemCount
) {
}
