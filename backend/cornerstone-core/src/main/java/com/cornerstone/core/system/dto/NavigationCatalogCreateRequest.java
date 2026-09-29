package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建一个顶层分组（sys_menu 的 catalog 节点）。
 *
 * 目录不挂权限码、不带路由，所以这一条请求能安全地由客户自己发：建完是个空分组，
 * 要往里面放页面还得再拖，不会出现"看得见却 403"的入口。
 */
@Schema(description = "新建顶层分组请求")
public record NavigationCatalogCreateRequest(
    @Schema(description = "分组名称") @NotBlank(message = "分组名称不能为空")
    @Size(max = 128, message = "分组名称长度不能超过128个字符") String menuName,
    @Schema(description = "图标名称(空=用默认图标)") @Size(max = 128, message = "图标名称长度不能超过128个字符") String icon,
    @Schema(description = "分组编码(留空由系统生成 nav_ 前缀的编码)")
    @Size(max = 64, message = "分组编码长度不能超过64个字符") String menuCode
) {
}
