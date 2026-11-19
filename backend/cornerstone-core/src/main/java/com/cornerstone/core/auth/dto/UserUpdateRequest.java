package com.cornerstone.core.auth.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @NotNull(message = "机构ID不能为空")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long institutionId,
    @NotBlank(message = "显示名称不能为空") @Size(max = 128, message = "显示名称长度不能超过128个字符") String displayName,
    @Size(max = 32, message = "手机号长度不能超过32个字符") String phone,
    @NotBlank(message = "状态不能为空") @Size(max = 32, message = "状态长度不能超过32个字符") String status,
    @Size(max = 500, message = "备注长度不能超过500个字符") String remark
) {
}
