package com.cornerstone.module.organization.dto;

import java.time.LocalDate;

public record CampusUpdateRequest(
    String campusCode,
    String campusName,
    String campusType,
    String phone,
    String principalName,
    String provinceCode,
    String cityCode,
    String districtCode,
    String address,
    Integer capacityLimit,
    String status,
    LocalDate openedAt,
    Integer sortNo,
    String remark
) {
}