package com.cornerstone.core.system.service;

import com.cornerstone.core.system.action.ActionDefinition;
import com.cornerstone.core.system.dto.ActionCatalogView;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按钮授权目录：把各模块代码登记的"页面 → 操作项"与 {@code sys_menu} 节点合并，
 * 供角色授权界面展示与保存校验使用。目录本身不存储授权。
 */
public interface ActionCatalogService {

    /**
     * 查询授权目录。
     *
     * @param roleId 可选；传入时给每个动作标出 grantedByNode / grantedByCsv，用于回显勾选态
     */
    ActionCatalogView catalog(Long roleId);

    /** 授权界面可管理的节点：全部管理端页面节点 + 目录内动作的授权节点 */
    Set<Long> managedMenuIds();

    /** 动作业务键 → 所属页面节点ID，保存授权时据此补祖先目录 */
    Map<String, Long> pageMenuIdByActionKey();

    /** 授权节点ID → 所属页面节点ID；按钮节点的 parent 不一定是它的业务页面（如档案维护跨两页） */
    Map<Long, Long> pageMenuIdByGrantMenuId();

    /** 全部代码登记的定义，供缺口校验与测试使用 */
    List<ActionDefinition> allDefinitions();
}
