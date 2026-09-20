package com.jiaopei.platform.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "已关联的学员档案摘要")
public record StudentBriefView(
    @Schema(description = "学员ID")
    Long id,
    @Schema(description = "学员编号")
    String studentNo,
    @Schema(description = "学员姓名")
    String studentName,
    @Schema(description = "性别(MALE=男,FEMALE=女,UNKNOWN=未知)")
    String gender,
    @Schema(description = "班级名称(取当前所在班,无则为空)")
    String className,
    @Schema(description = "备注")
    String remark
) {
}