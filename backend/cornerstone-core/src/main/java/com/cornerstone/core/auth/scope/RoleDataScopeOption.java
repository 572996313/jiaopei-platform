package com.cornerstone.core.auth.scope;

/**
 * 数据范围维度下的一个可授权节点。
 *
 * @param value      节点ID
 * @param code       节点编码
 * @param name       节点名称
 * @param status     节点状态（由业务模块定义）
 * @param selectable 是否还能被新选中；停用节点仍会返回，便于管理员从已授权列表中移除
 */
public record RoleDataScopeOption(Long value, String code, String name, String status, Boolean selectable) {
}
