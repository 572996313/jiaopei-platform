package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "我的订阅")
public record MessageSubscriptionView(
    @Schema(description = "订阅规则；空列表=未收窄，看到全部可见事件") List<MessageSubscriptionRuleView> rules
) {
}
