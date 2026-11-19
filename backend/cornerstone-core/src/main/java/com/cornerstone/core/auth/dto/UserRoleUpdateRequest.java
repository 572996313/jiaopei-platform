package com.cornerstone.core.auth.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UserRoleUpdateRequest(
    @NotNull(message = "角色ID列表不能为空")
    @JsonDeserialize(contentUsing = FlexibleLongIdDeserializer.class)
    List<Long> roleIds
) {
}
