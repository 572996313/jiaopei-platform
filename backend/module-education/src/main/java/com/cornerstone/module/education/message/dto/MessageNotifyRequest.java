package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "提醒某人查看事件请求")
public class MessageNotifyRequest {

    @Schema(description = "被提醒的账号ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少要选择一位被提醒人")
    @Size(max = 100, message = "一次最多提醒 100 人")
    private List<@NotNull(message = "被提醒人账号ID不能为空") Long> userIds;

    @Schema(description = "提醒留言")
    @Size(max = 500, message = "提醒留言长度不能超过 500")
    private String note;
}
