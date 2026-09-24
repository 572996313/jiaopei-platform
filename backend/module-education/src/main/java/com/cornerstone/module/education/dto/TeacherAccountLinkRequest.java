package com.cornerstone.module.education.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 确认绑定教师档案与登录账号。
 *
 * <p>只接受「档案ID + 账号ID」这一对显式结论：服务端不重跑手机号匹配，
 * 匹配口径的解释留在预览里，落库时只做归属与占用校验。</p>
 */
@Schema(description = "教师档案绑定登录账号请求")
public record TeacherAccountLinkRequest(
    @Schema(description = "要绑定的档案与账号配对") @NotEmpty(message = "请至少选择一位教师")
    @Valid List<Link> links
) {

    @Schema(description = "教师档案与登录账号的一对配对")
    public record Link(
        @Schema(description = "教师档案ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
        @NotNull(message = "教师档案ID不能为空")
        Long teacherId,
        @Schema(description = "登录账号ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
        @NotNull(message = "登录账号ID不能为空")
        Long userId
    ) {
    }
}
