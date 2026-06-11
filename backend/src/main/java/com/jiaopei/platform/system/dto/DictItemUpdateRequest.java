package com.jiaopei.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DictItemUpdateRequest(
    @NotBlank @Size(max = 128) String itemName,
    @NotBlank @Size(max = 128) String itemValue,
    Integer sortNo,
    @NotBlank @Size(max = 32) String status,
    @Size(max = 500) String remark
) {
}
