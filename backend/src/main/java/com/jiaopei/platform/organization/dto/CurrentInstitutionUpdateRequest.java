package com.jiaopei.platform.organization.dto;

import jakarta.validation.constraints.Size;

public record CurrentInstitutionUpdateRequest(
    @Size(max = 128) String name,
    @Size(max = 64) String shortName,
    @Size(max = 64) String contactName,
    @Size(max = 32) String contactPhone,
    @Size(max = 255) String address,
    @Size(max = 32) String status,
    @Size(max = 500) String remark
) {
}
