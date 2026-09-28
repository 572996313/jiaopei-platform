package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "小程序首页课次(跨绑定学员合并,教师只给姓)")
public record MiniappHomeLessonView(
    @Schema(description = "该班级里属于当前账号的学员姓名,同班两个孩子时用顿号连接")
    String studentName,
    @Schema(description = "上课日期")
    LocalDate lessonDate,
    @Schema(description = "开始时间")
    LocalTime startTime,
    @Schema(description = "结束时间")
    LocalTime endTime,
    @Schema(description = "课程名称")
    String courseName,
    @Schema(description = "班级名称")
    String className,
    @Schema(description = "教室名称")
    String classroomName,
    @Schema(description = "授课教师称呼(只给姓+老师)")
    String teacherName,
    @Schema(description = "课次状态(scheduled=已排课,rescheduled=已调课,makeup=已补课,completed=已结课;cancelled=已停课不下发)")
    String lessonStatus
) {
}
