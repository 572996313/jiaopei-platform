package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "当前账号已绑定的第三方身份")
public record UserIdentityView(
    @Schema(description = "绑定关系ID") Long id,
    @Schema(description = "渠道编码") String providerCode,
    @Schema(description = "渠道名称") String providerName,
    @Schema(description = "应用类型") String appType,
    @Schema(description = "外部昵称快照") String nickname,
    @Schema(description = "绑定时间") LocalDateTime createdAt,
    @Schema(description = "最近一次通过该身份登录时间") LocalDateTime lastLoginAt
) {
}
