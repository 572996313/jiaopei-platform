package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "当前登录用户信息")
public record CurrentUserResponse(
    @Schema(description = "用户ID") Long userId,
    @Schema(description = "机构ID") Long orgId,
    @Schema(description = "机构ID(兼容)") Long institutionId,
    @Schema(description = "默认校区ID") Long defaultCampusId,
    @Schema(description = "用户名") String username,
    @Schema(description = "显示名称") String displayName,
    @Schema(description = "角色码列表") List<String> roles,
    @Schema(description = "权限码列表") List<String> permissions,
    @Schema(description = "密码重置标记") Boolean passwordResetRequired,
    @Schema(description = "强制改密生效值") Boolean forceChangePassword,
    @Schema(description = "所属端(admin=平台用户/管理后台;其余取值由业务模块注册的 PortalResolver 判定,本项目小程序端为 student/parent)")
    String portal
) {
}
