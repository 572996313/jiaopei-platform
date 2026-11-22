package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 头像更新结果。
 *
 * <p>单独包一层而不是直接返回 {@code Long}：前端的大 ID 保护是按字段名模式
 * （{@code *Id}）把数字转字符串的，裸数值响应会丢精度。</p>
 */
@Schema(description = "头像更新结果")
public record AvatarView(
    @Schema(description = "头像文件ID(NULL=未设置)") Long avatarFileId
) {
}
