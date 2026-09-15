package com.jiaopei.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record LoginResponse(
    @Schema(description = "用户ID")
    Long userId,
    @Schema(description = "机构ID")
    Long orgId,
    @Schema(description = "用户名")
    String username,
    @Schema(description = "显示名称")
    String displayName,
    @Schema(description = "角色码列表")
    List<String> roles,
    @Schema(description = "权限码列表")
    List<String> permissions,
    @Schema(description = "令牌类型")
    String tokenType,
    @Schema(description = "访问令牌")
    String accessToken,
    @Schema(description = "是否需强制改密")
    Boolean forceChangePassword,
    @Schema(description = "所属端(admin=平台用户,student=学生端,parent=家长端)", allowableValues = {"admin", "student", "parent"})
    String portal
) {
}
