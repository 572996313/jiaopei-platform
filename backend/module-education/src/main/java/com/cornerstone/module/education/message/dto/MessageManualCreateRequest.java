package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "新建人工事项请求")
public class MessageManualCreateRequest {

    @Schema(description = "人工事件类型码(必须在事件目录里且可手选)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "事件类型不能为空")
    @Size(max = 64, message = "事件类型码长度不能超过 64")
    private String typeCode;

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过 200")
    private String title;

    @Schema(description = "正文(纯文本)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "正文不能为空")
    @Size(max = 5000, message = "正文长度不能超过 5000")
    private String body;

    @Schema(description = "级别覆盖(info/warning/critical)，空=沿用类型默认")
    @Pattern(regexp = "info|warning|critical", message = "级别只能是 info/warning/critical")
    private String level;

    @Schema(description = "校区锚点ID")
    private Long campusId;

    @Schema(description = "班级锚点ID")
    private Long classId;

    @Schema(description = "学员锚点ID")
    private Long studentId;

    @Schema(description = "教师锚点ID")
    private Long teacherId;

    @Schema(description = "指派人账号ID")
    private Long assigneeUserId;

    @Schema(description = "截止时间")
    private LocalDateTime dueAt;

    @Schema(description = "发生时间，空=当前时间")
    private LocalDateTime occurredAt;

    @Schema(description = "深链(只允许本系统页面路径)")
    @Size(max = 500, message = "深链长度不能超过 500")
    private String actionUrl;
}
