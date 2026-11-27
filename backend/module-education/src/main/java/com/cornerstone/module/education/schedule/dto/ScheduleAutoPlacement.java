package com.cornerstone.module.education.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 引擎排出的一条课次草案。预览接口直接返回它，执行时按同一份数据落库，不做第二次决策。
 */
@Schema(description = "自动排课落位结果")
public record ScheduleAutoPlacement(
    @Schema(description = "班级ID") Long classId,
    @Schema(description = "班级名称") String className,
    @Schema(description = "自然周序号(窗口内第几周,1 起)") int weekNo,
    @Schema(description = "上课日期") LocalDate lessonDate,
    @Schema(description = "星期(1=周一,7=周日)") Integer weekday,
    @Schema(description = "时段ID") Long timeSlotId,
    @Schema(description = "时段名称") String slotName,
    @Schema(description = "开始时间") LocalTime startTime,
    @Schema(description = "结束时间") LocalTime endTime,
    @Schema(description = "教室ID(沿用班级既有计划)") Long classroomId,
    @Schema(description = "教室名称") String classroomName,
    @Schema(description = "授课教师ID列表(沿用班级既有计划)") List<Long> teacherIds,
    @Schema(description = "授课教师姓名,顿号分隔") String teacherNames,
    @Schema(description = "落位来源(plan_gap=补上计划里没展开出的格子,free_cell=计划之外的空位)",
            allowableValues = {"plan_gap", "free_cell"}) String source,
    @Schema(description = "提示(如落在停排日),没有提示为空") String notice
) {
}
