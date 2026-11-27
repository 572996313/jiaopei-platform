package com.cornerstone.module.education.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 学员考勤明细行,GET /api/education/attendance/student-detail 返回。
 *
 * <p>只返回该学员"应到"的课次:当天在班或有考勤记录;attendanceStatus 为 null 表示该课次还没点到这位学员。
 */
@Schema(description = "学员考勤明细行视图")
public record AttendanceStudentLessonView(
    @Schema(description = "课次 ID") Long lessonId,
    @Schema(description = "班级 ID") Long classId,
    @Schema(description = "班级名称") String className,
    @Schema(description = "上课日期") LocalDate lessonDate,
    @Schema(description = "开始时间") LocalTime startTime,
    @Schema(description = "结束时间") LocalTime endTime,
    @Schema(description = "课次状态(scheduled=已排课,rescheduled=已调课,cancelled=已停课,makeup=已补课,completed=已结课)",
        allowableValues = {"scheduled", "rescheduled", "cancelled", "makeup", "completed"}) String lessonStatus,
    @Schema(description = "课次标题") String lessonTitle,
    @Schema(description = "出勤状态(present=出勤,absent=缺勤,late=迟到,leave=请假);null=未点名",
        allowableValues = {"present", "absent", "late", "leave", "null"}) String attendanceStatus,
    @Schema(description = "数据来源(MANUAL=手动,AUTO=自动,IMPORT=导入)") String source,
    @Schema(description = "备注") String remark
) {
}
