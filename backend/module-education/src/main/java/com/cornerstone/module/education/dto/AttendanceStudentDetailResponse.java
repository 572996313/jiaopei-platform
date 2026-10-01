package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 学员考勤明细响应,GET /api/education/attendance/student-detail 返回。
 *
 * <p>汇总卡与明细行一次返回;lessonCount 即该学员区间内的应到课次数。
 */
@Schema(description = "学员考勤明细响应")
public record AttendanceStudentDetailResponse(
    @Schema(description = "学员 ID") Long studentId,
    @Schema(description = "学号") String studentNo,
    @Schema(description = "学员姓名") String studentName,
    @Schema(description = "统计区间起始日期") LocalDate rangeStart,
    @Schema(description = "统计区间截止日期") LocalDate rangeEnd,
    @Schema(description = "区间合计") AttendanceSummaryTotalsView summary,
    @Schema(description = "应到课次明细(按日期与节次排序)") List<AttendanceStudentLessonView> lessons
) {
}
