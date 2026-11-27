package com.cornerstone.module.education.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "自动排课批次(历史清单与详情)")
public record ScheduleAutoBatchView(
    @Schema(description = "批次ID") Long id,
    @Schema(description = "机构ID") Long institutionId,
    @Schema(description = "校区ID") Long campusId,
    @Schema(description = "学期ID") Long termId,
    @Schema(description = "补排窗口起始日期") LocalDate startDate,
    @Schema(description = "补排窗口截止日期") LocalDate endDate,
    @Schema(description = "本次纳入的班级ID列表(空=该校区全部待补班级)") List<Long> classIds,
    @Schema(description = "本批生效的择优策略") ScheduleAutoStrategy strategy,
    @Schema(description = "待补节数") Integer requestedCount,
    @Schema(description = "生成的课次数") Integer generatedCount,
    @Schema(description = "没补上的节数") Integer unscheduledCount,
    @Schema(description = "没补上的明细") List<ScheduleAutoFailure> failures,
    @Schema(description = "批次状态(completed=已执行,rolled_back=已撤销)",
            allowableValues = {"completed", "rolled_back"}) String status,
    @Schema(description = "撤销时间(未撤销为空)") LocalDateTime rolledBackAt,
    @Schema(description = "备注") String remark,
    @Schema(description = "执行时间") LocalDateTime createdAt
) {
}
