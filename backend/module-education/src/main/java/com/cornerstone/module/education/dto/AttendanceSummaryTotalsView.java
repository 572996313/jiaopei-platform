package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 考勤汇总合计视图,summary 接口与学员考勤明细接口共用。
 *
 * <p>课次维度的计数以"应到>0 且还有学员没点到"为未点名口径,与点名页一致。
 */
@Schema(description = "考勤汇总合计视图")
public record AttendanceSummaryTotalsView(
    @Schema(description = "课次数量(按学员分组时为该学员的应到课次数)") int lessonCount,
    @Schema(description = "已点名课次数(应到>0 且全部点到;按学员分组时为有考勤记录的课次数)") int fullyMarkedLessons,
    @Schema(description = "未点名课次数(应到>0 但还有学员没点到;按学员分组时为没有考勤记录的应到课次数)") int pendingLessons,
    @Schema(description = "应到人次(按学员分组时与应到课次数相同)") int expectedCount,
    @Schema(description = "出勤人次") int presentCount,
    @Schema(description = "迟到人次") int lateCount,
    @Schema(description = "请假人次") int leaveCount,
    @Schema(description = "缺勤人次") int absentCount
) {
}
