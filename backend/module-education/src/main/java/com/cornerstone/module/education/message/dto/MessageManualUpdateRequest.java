package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "编辑人工事项/流转状态请求")
public class MessageManualUpdateRequest {

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过 200")
    private String title;

    @Schema(description = "正文(纯文本)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "正文不能为空")
    @Size(max = 5000, message = "正文长度不能超过 5000")
    private String body;

    @Schema(description = "级别(info/warning/critical)")
    @Pattern(regexp = "info|warning|critical", message = "级别只能是 info/warning/critical")
    private String level;

    @Schema(description = "指派人账号ID")
    private Long assigneeUserId;

    @Schema(description = "截止时间")
    private LocalDateTime dueAt;

    @Schema(description = "状态(open/in_progress/done/closed)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "open|in_progress|done|closed", message = "状态只能是 open/in_progress/done/closed")
    private String status;

    @Schema(description = "本次流转说明(进时间线)")
    @Size(max = 1000, message = "流转说明长度不能超过 1000")
    private String comment;

    @Schema(description = "深链(只允许本系统页面路径)")
    @Size(max = 500, message = "深链长度不能超过 500")
    private String actionUrl;
}
