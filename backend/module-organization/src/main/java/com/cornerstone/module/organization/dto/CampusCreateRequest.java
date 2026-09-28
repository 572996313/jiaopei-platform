package com.cornerstone.module.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CampusCreateRequest(
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    // 可留空：留空时由编号规则补号，判空在 Service 里做
    String campusCode,
    @NotBlank(message = "校区名称不能为空")
    String campusName,
    @Schema(description = "校区类型字典(campus_type:direct=直营,franchise=加盟,partner=合作)", allowableValues = {"direct", "franchise", "partner"})
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
