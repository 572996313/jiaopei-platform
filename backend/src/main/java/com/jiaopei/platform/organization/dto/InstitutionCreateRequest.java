package com.jiaopei.platform.organization.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record InstitutionCreateRequest(
    @NotBlank(message = "orgCode is required")
    String orgCode,
    @NotBlank(message = "orgName is required")
    String orgName,
    String shortName,
    String contactName,
    String contactPhone,
    String provinceCode,
    String cityCode,
    String districtCode,
    String address,
    String timezone,
    String status,
    LocalDate openedAt,
    String remark
) {
}
