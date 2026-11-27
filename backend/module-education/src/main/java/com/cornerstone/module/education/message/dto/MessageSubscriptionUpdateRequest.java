package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "保存我的订阅规则(整表替换)")
public class MessageSubscriptionUpdateRequest {

    @Schema(description = "订阅规则列表；传空数组等于取消全部订阅，回到「全部可见事件」",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "订阅规则列表不能为空")
    @Size(max = 200, message = "订阅规则不能超过 200 条")
    private List<@Valid MessageSubscriptionRuleInput> rules;
}
