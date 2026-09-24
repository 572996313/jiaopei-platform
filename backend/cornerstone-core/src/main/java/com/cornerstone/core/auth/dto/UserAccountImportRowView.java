package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 导入预览/结果里的单行回显。
 *
 * <p>不回显口令：文件能整份留档，把口令再抄进接口响应等于多一处明文外泄。</p>
 */
@Schema(description = "用户账号导入的单行结果")
public record UserAccountImportRowView(
    @Schema(description = "Excel 行号,含表头偏移,便于客户回到原文件定位") Integer rowNum,
    @Schema(description = "登录账号") String username,
    @Schema(description = "显示名称") String displayName,
    @Schema(description = "手机号") String phone,
    @Schema(description = "角色编码,原样回显") String roleCodes,
    @Schema(description = "状态,留空回显为 active") String status,
    @Schema(description = "本行是否校验通过") boolean valid,
    @Schema(description = "校验失败原因,多条用；分隔;通过时为 null") String error,
    @Schema(description = "非阻断提示,如账号已存在将跳过;无提示时为 null") String notice,
    @Schema(description = "落库后的账号ID;预览阶段为 null") Long userId
) {
}
