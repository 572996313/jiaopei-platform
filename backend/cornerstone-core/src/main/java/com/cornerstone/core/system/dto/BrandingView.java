package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record BrandingView(
    @Schema(description = "系统名称,展示在横幅主标题与登录页引子;空串表示未配置")
    String name,
    @Schema(description = "定位副标题,展示在横幅名称下方与登录页大标题;空串表示未配置")
    String tagline,
    @Schema(description = "窄栏角标,取系统名称首字符推导,不单独配置")
    String mark,
    @Schema(description = "登录页一句话介绍;空串表示未配置")
    String loginCopy,
    @Schema(description = "登录表单标题;空串表示未配置")
    String loginFormTitle
) {
}
