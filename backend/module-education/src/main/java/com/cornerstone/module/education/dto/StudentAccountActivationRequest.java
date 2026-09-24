package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "批量生成激活码请求")
public record StudentAccountActivationRequest(
    @Schema(description = "待激活账号ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "账号列表不能为空") @Size(max = 1000, message = "单次最多生成1000个激活码")
    List<Long> userIds,

    @Schema(description = "激活码有效天数,不填按系统默认7天")
    Integer validDays
) {
}
