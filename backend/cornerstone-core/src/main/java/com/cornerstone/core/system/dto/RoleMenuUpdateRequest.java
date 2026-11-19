package com.cornerstone.core.system.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RoleMenuUpdateRequest(
    @NotNull(message = "菜单ID列表不能为空")
    @JsonDeserialize(contentUsing = FlexibleLongIdDeserializer.class)
    List<Long> menuIds
) {
}
