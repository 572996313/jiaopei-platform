package com.jiaopei.platform.organization.dto;

import jakarta.validation.constraints.Size;

public record CurrentInstitutionUpdateRequest(
    @Size(max = 128, message = "机构名称长度不能超过128个字符") String name,
    @Size(max = 64, message = "机构简称长度不能超过64个字符") String shortName,
    @Size(max = 64, message = "联系人长度不能超过64个字符") String contactName,
    @Size(max = 32, message = "联系电话长度不能超过32个字符") String contactPhone,
    @Size(max = 255, message = "地址长度不能超过255个字符") String address,
    @Size(max = 32, message = "状态长度不能超过32个字符") String status,
    @Size(max = 500, message = "备注长度不能超过500个字符") String remark
) {
}
