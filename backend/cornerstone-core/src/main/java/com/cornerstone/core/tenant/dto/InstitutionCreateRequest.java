package com.cornerstone.core.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record InstitutionCreateRequest(
    // 可留空：留空时由编号规则补号，判空在 Service 里做
    String orgCode,
    @NotBlank(message = "机构名称不能为空")
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
