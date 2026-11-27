package com.cornerstone.module.education.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 没补上的那一节，以及机器给出的原因。批次表只存这一份明细——排上的课次本身就是证据。
 */
@Schema(description = "自动排课未落位明细")
public record ScheduleAutoFailure(
    @Schema(description = "班级ID") Long classId,
    @Schema(description = "班级名称") String className,
    @Schema(description = "自然周序号(窗口内第几周,1 起;整班未纳入时为空)") Integer weekNo,
    @Schema(description = "没补上的原因(中文,操作员能照着去改配置)") String reason
) {
}
