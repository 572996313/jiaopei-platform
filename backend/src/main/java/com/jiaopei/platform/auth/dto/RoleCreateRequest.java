package com.jiaopei.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RoleCreateRequest(
    @NotBlank @Size(max = 64) String roleCode,
    @NotBlank @Size(max = 128) String roleName,
    @NotNull List<String> permissionCodes,
    @Size(max = 32) String status,
    @Size(max = 500) String remark
) {
}
