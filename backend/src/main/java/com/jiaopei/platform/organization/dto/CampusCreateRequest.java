package com.jiaopei.platform.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CampusCreateRequest(
    @NotNull(message = "institutionId 不能为空")
    Long institutionId,
    @NotBlank(message = "campusCode 不能为空")
    String campusCode,
    @NotBlank(message = "campusName 不能为空")
    String campusName,
    String campusType,
    String phone,
    String principalName,
    String provinceCode,
    String cityCode,
    String districtCode,
    @NotBlank(message = "address 不能为空")
    String address,
    Integer capacityLimit,
    String status,
    LocalDate openedAt,
    Integer sortNo,
    String remark
) {
}
