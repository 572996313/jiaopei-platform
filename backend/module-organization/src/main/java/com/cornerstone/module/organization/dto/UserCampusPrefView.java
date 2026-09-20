package com.cornerstone.module.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户默认校区偏好视图")
public record UserCampusPrefView(
    @Schema(description = "用户ID") Long userId,
    @Schema(description = "用户名") String username,
    @Schema(description = "显示名称") String displayName,
    @Schema(description = "默认校区ID,为空表示未设置") Long campusId
) {
}
