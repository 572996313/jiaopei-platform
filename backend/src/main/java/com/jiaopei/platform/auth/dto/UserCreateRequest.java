package com.jiaopei.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
    @NotNull Long institutionId,
    Long defaultCampusId,
    @NotBlank @Size(max = 64) String username,
    @NotBlank @Size(max = 128) String displayName,
    @Size(max = 32) String phone,
    @Size(min = 8, max = 128) String initialPassword,
    @Size(max = 32) String status,
    @Size(max = 500) String remark
) {
}
