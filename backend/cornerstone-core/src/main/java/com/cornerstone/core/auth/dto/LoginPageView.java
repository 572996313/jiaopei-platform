package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginPageView(
    @Schema(description = "登录页表单下方提示语,空串表示不展示")
    String tip
) {
}
