package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "小程序同学名单(按当前班级分组)")
public record MiniappClassmateGroupView(
    @Schema(description = "班级ID")
    Long classId,
    @Schema(description = "班级名称")
    String className,
    @Schema(description = "在班人数(含本人)")
    Integer total,
    @Schema(description = "同学姓名(不含本人;只给姓名,学号/性别/生日等标识一律不下发)")
    List<String> classmateNames
) {
}
