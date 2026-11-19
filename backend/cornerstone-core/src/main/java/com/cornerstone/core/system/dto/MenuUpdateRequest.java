package com.cornerstone.core.system.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MenuUpdateRequest(
    // 雪花 Long：前端按字符串下发，禁止经过 JS Number（19 位会被截断）
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long parentId,
    @NotBlank(message = "菜单名称不能为空") @Size(max = 128, message = "菜单名称长度不能超过128个字符") String menuName,
    @NotBlank(message = "菜单类型不能为空") @Size(max = 32, message = "菜单类型长度不能超过32个字符") String menuType,
    @Size(max = 255, message = "路由路径长度不能超过255个字符") String path,
    @Size(max = 255, message = "组件路径长度不能超过255个字符") String component,
    @Size(max = 128, message = "权限编码长度不能超过128个字符") String permissionCode,
    @Size(max = 128, message = "图标长度不能超过128个字符") String icon,
    Integer sortNo,
    Boolean visible,
    @NotBlank(message = "状态不能为空") @Size(max = 32, message = "状态长度不能超过32个字符") String status,
    @Size(max = 500, message = "备注长度不能超过500个字符") String remark
) {
}
