package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "可指派人(后台员工账号)")
public record MessageAssigneeView(
    @Schema(description = "账号ID") Long userId,
    @Schema(description = "显示名") String displayName,
    @Schema(description = "登录账号") String username
) {
}
