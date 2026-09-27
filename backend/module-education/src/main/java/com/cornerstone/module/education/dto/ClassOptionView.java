package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "班级下拉选项(只带下拉与回显需要的字段,供按需搜索使用)")
public record ClassOptionView(
    @Schema(description = "班级ID")
    Long id,
    @Schema(description = "班级编码")
    String classCode,
    @Schema(description = "班级名称")
    String className,
    @Schema(description = "所属校区ID(排课弹窗据此带出该校区的时间段)")
    Long campusId,
    @Schema(description = "所属学期ID(排课计划弹窗据此预填学期)")
    Long termId
) {
}
