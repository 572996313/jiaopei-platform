package com.jiaopei.platform.system.dto;

import java.util.List;

public record MenuView(
    Long id,
    Long parentId,
    String menuCode,
    String menuName,
    String menuType,
    String path,
    String component,
    String permissionCode,
    String icon,
    Integer sortNo,
    Boolean visible,
    String status,
    String remark,
    List<MenuView> children
) {
}
