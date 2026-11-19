package com.cornerstone.core.system.action;

import java.util.List;

/**
 * 按钮授权项的代码侧登记入口（SPI）。
 *
 * <p>底座只定义接口与合并逻辑，具体动作由各模块在自己的 {@code config} 包实现并注册为 Bean。
 * 模块关闭时实现随之消失，目录自然少掉该段；底座不 import 任何业务包。</p>
 *
 * <p>新增按钮时按 {@link ActionDefinition} 补一条，无需改数据库即可出现在角色授权界面；
 * 需要独立授权（{@link ActionMode#STANDALONE}）时才额外新增按钮节点与权限码迁移。</p>
 */
public interface ActionCatalogContributor {

    /** 模块标识，仅用于排障与缺口提示，如 core / education / organization */
    String moduleKey();

    List<ActionDefinition> actions();
}
