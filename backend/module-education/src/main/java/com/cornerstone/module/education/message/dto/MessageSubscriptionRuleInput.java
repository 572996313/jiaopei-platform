package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "单条订阅规则")
public class MessageSubscriptionRuleInput {

    @Schema(description = "维度(type_code/category/campus/class/student/teacher/level/keyword)",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "订阅维度不能为空")
    @Size(max = 16, message = "订阅维度长度不能超过 16")
    private String ruleType;

    @Schema(description = "维度值", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "订阅值不能为空")
    @Size(max = 128, message = "订阅值长度不能超过 128")
    private String ruleValue;

    @Schema(description = "是否启用，空=true")
    private Boolean enabled;
}
