package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "第三方登录可选择的账号")
public record AccountOptionView(
    @Schema(description = "不透明账号键(仅本次登录票据内有效，不暴露系统用户ID)") String key,
    @Schema(description = "账号显示名") String displayName,
    @Schema(description = "所属端(admin=管理后台;其余取值由业务侧 PortalResolver 判定)") String portal,
    @Schema(description = "所属机构名称") String institutionName
) {
}
