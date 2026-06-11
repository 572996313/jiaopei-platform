package com.jiaopei.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DictItemCreateRequest(
    @NotNull Long typeId,
    @NotBlank @Size(max = 64) String itemCode,
    @NotBlank @Size(max = 128) String itemName,
    @NotBlank @Size(max = 128) String itemValue,
    Integer sortNo,
    @Size(max = 32) String status,
    @Size(max = 500) String remark
) {
}
