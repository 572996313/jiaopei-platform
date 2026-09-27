package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "自动排课预览/执行结果")
public record ScheduleAutoPlanResponse(
    @Schema(description = "是否已落库(false=预览,只给草案)") boolean executed,
    @Schema(description = "批次ID(预览为空)") Long batchId,
    @Schema(description = "补排窗口起始日期") LocalDate startDate,
    @Schema(description = "补排窗口截止日期") LocalDate endDate,
    @Schema(description = "待补节数(进入引擎的班级在窗口内的缺口合计;整班退回的班级不在这里)") int requestedCount,
    @Schema(description = "排上位节数(预览=草案条数,执行=已生成课次数)") int generatedCount,
    @Schema(description = "没补上的节数(待补减排上位;一条原因可能对应多节,原因本身看 failures)") int unscheduledCount,
    @Schema(description = "落位明细(预览全列;执行后同样回显本次生成的课次)") List<ScheduleAutoPlacement> placements,
    @Schema(description = "没补上的明细") List<ScheduleAutoFailure> failures
) {
}
