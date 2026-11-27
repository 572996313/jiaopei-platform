package com.cornerstone.module.education.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "排课冲突检测响应")
public record ScheduleConflictCheckResponse(
    @Schema(description = "是否存在硬冲突(为 true 时不允许保存)")
    Boolean conflict,
    @Schema(description = "冲突类型列表(class=班级占用,classroom=教室占用,teacher=教师占用,rule=禁排规则)")
    List<String> conflictTypes,
    @Schema(description = "冲突提示信息列表,与 conflictTypes 一一对应")
    List<String> messages,
    @Schema(description = "只提示不拦的提醒列表(student=学员跨班撞课,teacher_load=教师超课时上限,calendar=撞停排日)。"
        + "硬冲突照样拦,这几类是操作员自己判断该不该照排")
    List<String> notices
) {
}
