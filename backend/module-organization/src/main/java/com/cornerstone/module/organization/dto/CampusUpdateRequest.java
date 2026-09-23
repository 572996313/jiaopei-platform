package com.cornerstone.module.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record CampusUpdateRequest(
    String campusCode,
    String campusName,
    @Schema(description = "校区类型字典(campus_type:direct=直营,franchise=加盟,partner=合作)", allowableValues = {"direct", "franchise", "partner"})
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