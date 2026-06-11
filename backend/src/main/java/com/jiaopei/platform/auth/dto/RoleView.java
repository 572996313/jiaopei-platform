package com.jiaopei.platform.auth.dto;

import java.util.List;

public record RoleView(
    Long id,
    String roleCode,
    String roleName,
    List<String> permissionCodes,
    String status,
    String remark
) {
}
