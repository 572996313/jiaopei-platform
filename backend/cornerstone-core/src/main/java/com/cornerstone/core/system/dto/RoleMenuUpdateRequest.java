package com.cornerstone.core.system.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RoleMenuUpdateRequest(
    @NotNull(message = "菜单ID列表不能为空")List<Long> menuIds
) {
}
