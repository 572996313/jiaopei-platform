package com.cornerstone.core.system.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 导航编排的单个节点改动。
 *
 * **只有这五个字段可写**（名称、图标、顺序、所属分组、是否可见）。路由、权限码、类型、编码
 * 一律不在这里：改它们会让入口指向不存在的页面或把人挡在 403 之外，那属菜单管理的职责。
 */
@Schema(description = "导航编排节点改动")
public record NavigationNodeUpdateRequest(
    // 雪花 Long：前端按字符串下发，禁止经过 JS Number（19 位会被截断）
    @Schema(description = "菜单节点ID") @NotNull(message = "菜单节点ID不能为空")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long id,
    @Schema(description = "分组名或页面名") @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称长度不能超过128个字符") String menuName,
    @Schema(description = "图标名称(空=不显示图标)") @Size(max = 128, message = "图标名称长度不能超过128个字符") String icon,
    @Schema(description = "排序号") @NotNull(message = "排序号不能为空") Integer sortNo,
    @Schema(description = "父节点ID(空=提升为顶层分组)")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long parentId,
    @Schema(description = "是否在侧边栏可见") @NotNull(message = "可见性不能为空") Boolean visible
) {
}
