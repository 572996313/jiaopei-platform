package com.cornerstone.module.education.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "引用某节次的课次(节次明细的「查看引用」用)")
public record TimeSlotLessonRefView(
    @Schema(description = "课次ID")
    Long lessonId,
    @Schema(description = "上课日期")
    LocalDate lessonDate,
    @Schema(description = "星期(1=周一,7=周日)")
    Integer lessonWeekday,
    @Schema(description = "开始时间")
    LocalTime startTime,
    @Schema(description = "结束时间")
    LocalTime endTime,
    @Schema(description = "班级ID")
    Long classId,
    @Schema(description = "班级名称(班级已删除时为空,前端回显 班级 #id)")
    String className,
    @Schema(description = "课次状态(scheduled=已排课,rescheduled=已调课,cancelled=已停课,makeup=已补课,completed=已结课)")
    String lessonStatus,
    @Schema(description = "课次是否已被删除(已删除的课次在任何课表页都不显示,但外键仍指着本节次,是删不掉节次的常见原因)")
    Boolean deleted
) {
}
