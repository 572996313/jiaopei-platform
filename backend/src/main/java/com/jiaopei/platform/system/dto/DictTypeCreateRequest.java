package com.jiaopei.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DictTypeCreateRequest(
    @NotBlank @Size(max = 64) String typeCode,
    @NotBlank @Size(max = 128) String typeName,
    @Size(max = 32) String status,
    @Size(max = 500) String remark
) {
}
