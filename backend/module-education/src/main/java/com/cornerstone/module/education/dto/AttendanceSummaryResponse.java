package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 考勤汇总响应,GET /api/education/attendance/summary 返回。
 *
 * <p>summary 是区间内的课次级合计,与分组维度无关;rows 的结构见 {@link AttendanceSummaryRowView}。
 */
@Schema(description = "考勤汇总响应")
public record AttendanceSummaryResponse(
    @Schema(description = "统计区间起始日期") LocalDate rangeStart,
    @Schema(description = "统计区间截止日期") LocalDate rangeEnd,
    @Schema(description = "分组维度:class=按班级,student=按学员") String groupBy,
    @Schema(description = "区间合计") AttendanceSummaryTotalsView summary,
    @Schema(description = "汇总行(按班级或按学员)") List<AttendanceSummaryRowView> rows
) {
}
