package com.jiaopei.platform.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 权限码下拉选项:角色新增/编辑弹窗「预设权限」、菜单按钮 permissionCode 下拉、
 * 列表权限标签回显的公共数据源。
 * 以 {@code @RequiresPermission} 注解扫描注册表为权威源,合并菜单按钮节点中文名,
 * 前端无需维护静态清单,新增权限码自动出现。
 */
@Schema(description = "权限码下拉选项:以 @RequiresPermission 注解扫描为权威源,合并菜单按钮节点中文名")
public record PermissionOptionView(
    @Schema(description = "权限码,如 system:user:manage")
    String code,
    @Schema(description = "中文名:优先取按钮节点 menuName(多个时取第一个),无按钮节点时回退为权限码本身")
    String label,
    @Schema(description = "挂载该权限码的启用按钮节点中文名列表(可空,供前端分组与提示)")
    List<String> menuNames
) {
}