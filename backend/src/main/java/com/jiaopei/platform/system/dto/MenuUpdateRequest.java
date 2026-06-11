package com.jiaopei.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MenuUpdateRequest(
    Long parentId,
    @NotBlank @Size(max = 128) String menuName,
    @NotBlank @Size(max = 32) String menuType,
    @Size(max = 255) String path,
    @Size(max = 255) String component,
    @Size(max = 128) String permissionCode,
    @Size(max = 128) String icon,
    Integer sortNo,
    Boolean visible,
    @NotBlank @Size(max = 32) String status,
    @Size(max = 500) String remark
) {
}
