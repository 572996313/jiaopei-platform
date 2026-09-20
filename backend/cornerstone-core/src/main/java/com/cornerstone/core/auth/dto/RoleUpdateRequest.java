package com.cornerstone.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RoleUpdateRequest(
    @NotBlank(message = "角色名称不能为空") @Size(max = 128, message = "角色名称长度不能超过128个字符") String roleName,
    @NotNull(message = "权限编码列表不能为空") List<String> permissionCodes,
    @NotBlank(message = "状态不能为空") @Size(max = 32, message = "状态长度不能超过32个字符") String status,
    @Size(max = 500, message = "备注长度不能超过500个字符") String remark
) {
}
