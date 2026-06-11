package com.jiaopei.platform.auth.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UserRoleUpdateRequest(
    @NotNull List<Long> roleIds
) {
}
