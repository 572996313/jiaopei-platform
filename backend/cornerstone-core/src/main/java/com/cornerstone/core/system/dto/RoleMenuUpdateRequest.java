package com.cornerstone.core.system.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 角色授权保存请求：页面/按钮节点必填；数据范围可选。
 *
 * @param dataScopes null=不改动数据范围（兼容旧版前端）；非 null=按传入维度整组替换
 */
public record RoleMenuUpdateRequest(
    @NotNull(message = "菜单ID列表不能为空")
    @JsonDeserialize(contentUsing = FlexibleLongIdDeserializer.class)
    List<Long> menuIds,
    @Valid
    List<RoleDataScopeUpdate> dataScopes
) {

    public RoleMenuUpdateRequest(List<Long> menuIds) {
        this(menuIds, null);
    }
}
