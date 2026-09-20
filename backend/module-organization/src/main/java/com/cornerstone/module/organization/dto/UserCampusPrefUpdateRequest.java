package com.cornerstone.module.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "设置用户默认校区入参")
public record UserCampusPrefUpdateRequest(
    @Schema(description = "默认校区ID,传 null 表示清除该用户的默认校区") Long campusId
) {
}
