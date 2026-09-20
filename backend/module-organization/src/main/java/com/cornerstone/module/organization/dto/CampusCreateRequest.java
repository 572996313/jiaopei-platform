package com.cornerstone.module.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CampusCreateRequest(
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @NotBlank(message = "校区编码不能为空")
    String campusCode,
    @NotBlank(message = "校区名称不能为空")
    String campusName,
    String campusType,
    String phone,
    String principalName,
    String provinceCode,
    String cityCode,
    String districtCode,
    @NotBlank(message = "地址不能为空")
    String address,
    Integer capacityLimit,
    String status,
    LocalDate openedAt,
    Integer sortNo,
    String remark
) {
}
