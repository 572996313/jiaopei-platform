package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "小程序课表条目(含班级/课程/教室/教师信息)")
public record MiniappLessonView(
    @Schema(description = "课次ID")
    Long lessonId,
    @Schema(description = "上课日期")
    LocalDate lessonDate,
    @Schema(description = "星期(1=周一,7=周日)")
    Integer weekday,
    @Schema(description = "开始时间")
    LocalTime startTime,
    @Schema(description = "结束时间")
    LocalTime endTime,
    @Schema(description = "课次标题")
    String lessonTitle,
    @Schema(description = "课次状态(scheduled=已排课,rescheduled=已调课,cancelled=已停课,makeup=已补课,completed=已结课)")
    String lessonStatus,
    @Schema(description = "班级名称")
    String className,
    @Schema(description = "课程名称")
    String courseName,
    @Schema(description = "教室名称")
    String classroomName,
    @Schema(description = "授课教师姓名")
    String teacherName,
    @Schema(description = "授课教师电话")
    String teacherPhone
) {
}