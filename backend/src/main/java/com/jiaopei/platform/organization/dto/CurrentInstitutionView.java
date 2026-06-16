package com.jiaopei.platform.organization.dto;

public record CurrentInstitutionView(
    Long id,
    String orgCode,
    String name,
    String shortName,
    String contactName,
    String contactPhone,
    String address,
    String status,
    String remark
) {
}
