package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "公告撤回请求")
public record AnnouncementRecallRequest(
    @Schema(description = "撤回原因(列表行内展示,作操作留痕)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "撤回原因不能为空")
    @Size(max = 200, message = "撤回原因不能超过 200 个字符")
    String reason
) {
}
