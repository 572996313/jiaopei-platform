package com.jiaopei.platform.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CampusCreateRequest(
    @NotNull(message = "institutionId is required")
    Long institutionId,
    @NotBlank(message = "campusCode is required")
    String campusCode,
    @NotBlank(message = "campusName is required")
    String campusName,
    String campusType,
    String phone,
    String principalName,
    String provinceCode,
    String cityCode,
    String districtCode,
    @NotBlank(message = "address is required")
    String address,
    Integer capacityLimit,
    String status,
    LocalDate openedAt,
    Integer sortNo,
    String remark
) {
}
