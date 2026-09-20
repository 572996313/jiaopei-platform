package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "排课冲突检测响应")
public record ScheduleConflictCheckResponse(
    @Schema(description = "是否存在冲突")
    Boolean conflict,
    @Schema(description = "冲突类型列表(CLASS=班级冲突,CLASSROOM=教室冲突,TEACHER=教师冲突)")
    List<String> conflictTypes,
    @Schema(description = "冲突提示信息列表")
    List<String> messages
) {
}
