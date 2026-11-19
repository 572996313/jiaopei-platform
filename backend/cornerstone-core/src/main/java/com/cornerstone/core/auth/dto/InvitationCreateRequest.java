package com.cornerstone.core.auth.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "创建邀请码请求")
public record InvitationCreateRequest(
    @Schema(description = "归属机构ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "机构ID不能为空")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long institutionId,

    @Schema(description = "注册成功后自动分配的角色编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "角色编码不能为空") @Size(max = 64, message = "角色编码长度不能超过64个字符") String roleCode,

    @Schema(description = "最大可用次数(不传为不限)")
    @Min(value = 1, message = "可用次数不能小于1") Integer maxUses,

    @Schema(description = "有效天数(不传为长期有效)")
    @Min(value = 1, message = "有效天数不能小于1") @Max(value = 3650, message = "有效天数不能超过3650") Integer validDays,

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过500个字符") String remark
) {
}
