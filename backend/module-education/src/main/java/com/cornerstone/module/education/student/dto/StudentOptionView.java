package com.cornerstone.module.education.student.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "学员下拉选项(只带下拉与回显需要的三个字段,供按需搜索使用)")
public record StudentOptionView(
    @Schema(description = "学员ID")
    Long id,
    @Schema(description = "学员编号")
    String studentNo,
    @Schema(description = "学员姓名")
    String studentName
) {
}
