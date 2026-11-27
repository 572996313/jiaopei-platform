package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "我的订阅规则视图")
public record MessageSubscriptionRuleView(
    @Schema(description = "维度(type_code/category/campus/class/student/teacher/level/keyword)") String ruleType,
    @Schema(description = "维度值") String ruleValue,
    @Schema(description = "展示名(类型/校区/班级/学员/教师名称，关键字原样)") String label,
    @Schema(description = "是否启用") boolean enabled
) {
}
