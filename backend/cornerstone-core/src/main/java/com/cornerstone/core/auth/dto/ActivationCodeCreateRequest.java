package com.cornerstone.core.auth.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "为待激活账号生成激活码请求")
public record ActivationCodeCreateRequest(
    @Schema(description = "待激活账号ID(status=pending_activation)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "账号ID不能为空")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long userId,

    @Schema(description = "激活码有效天数(不传默认7天)")
    @Min(value = 1, message = "有效天数不能小于1") Integer validDays
) {
}
