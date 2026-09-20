package com.jiaopei.platform.auth.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UserRoleUpdateRequest(
    @NotNull(message = "角色ID列表不能为空") List<Long> roleIds
) {
}
