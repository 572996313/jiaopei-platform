package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "事件阅读台账汇总(按类型)")
public record MessageReadLedgerSummaryView(
    @Schema(description = "类型码") String typeCode,
    @Schema(description = "类型名称") String typeName,
    @Schema(description = "分类") String category,
    @Schema(description = "产生方式") String sourceMode,
    @Schema(description = "事件总数") long totalCount,
    @Schema(description = "当前 active/open 数") long activeCount,
    @Schema(description = "已读人次(按已读行统计)") long readCount,
    @Schema(description = "订阅该类型的账号数") long subscriberCount,
    @Schema(description = "最近一次巡检时间") LocalDateTime lastRunAt,
    @Schema(description = "最近一次巡检命中行数") Integer lastRunCount,
    @Schema(description = "最近一次巡检失败原因") String lastError
) {
}
