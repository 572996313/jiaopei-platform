package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 考勤汇总行,按班级与按学员两种分组共用一套结构:
 * 按班级时填 classId/className,按学员时填 studentId/studentNo/studentName/classNames,另一侧为 null。
 *
 * <p>名称在服务端内存填充(参照完成度报表 ClassRow.className 的先例),前端不用再按 id 回查。
 */
@Schema(description = "考勤汇总行视图")
public record AttendanceSummaryRowView(
    @Schema(description = "班级 ID(按班级分组时有值)") Long classId,
    @Schema(description = "班级名称(按班级分组时有值)") String className,
    @Schema(description = "学员 ID(按学员分组时有值)") Long studentId,
    @Schema(description = "学号(按学员分组时有值)") String studentNo,
    @Schema(description = "学员姓名(按学员分组时有值)") String studentName,
    @Schema(description = "区间内上课的班级名称(按学员分组时有值,多班用列表)") List<String> classNames,
    @Schema(description = "课次数(按学员分组时为应到课次数)") int lessonCount,
    @Schema(description = "已点名课次数(应到>0 且全部点到;按学员分组时为有考勤记录的课次数)") int fullyMarkedLessons,
    @Schema(description = "未点名课次数(应到>0 但还有学员没点到;按学员分组时为没有考勤记录的应到课次数)") int pendingLessons,
    @Schema(description = "应到人次(按学员分组时与应到课次数相同)") int expectedCount,
    @Schema(description = "出勤人次") int presentCount,
    @Schema(description = "迟到人次") int lateCount,
    @Schema(description = "请假人次") int leaveCount,
    @Schema(description = "缺勤人次") int absentCount,
    @Schema(description = "出勤率(百分比整数,无应到人次时为 null)") Integer attendanceRate,
    @Schema(description = "按月小计(仅按班级分组时填充,按月份升序)") List<AttendanceMonthSubtotalView> months
) {
}
