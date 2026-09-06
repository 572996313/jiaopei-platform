package com.jiaopei.platform.organization.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record InstitutionCreateRequest(
    @NotBlank(message = "orgCode 不能为空")
    String orgCode,
    @NotBlank(message = "orgName 不能为空")
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
