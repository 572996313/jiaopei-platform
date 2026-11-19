package com.cornerstone.core.system.action;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 底座系统管理页面的按钮授权项登记。
 *
 * <p>系统管理页多为"读写同码"（码挂在页面节点上，V62/V105 已把同码按钮节点废弃），
 * 因此这些动作的 {@code grantMenuCode} 就是页面节点本身，模式统一为
 * {@link ActionMode#LINKED}：勾选页面即获得该页全部操作，界面会标明"读写同码"。
 * 只有系统参数与机构资料是读/写成对，写动作指向各自的按钮节点。</p>
 */
@Component("coreActionCatalogContributor")
public class CoreActionCatalogContributor implements ActionCatalogContributor {

    private static final String USER_MANAGE = "system:user:manage";
    private static final String ROLE_MANAGE = "system:role:manage";
    private static final String MENU_MANAGE = "system:menu:manage";

    @Override
    public String moduleKey() {
        return "core";
    }

    @Override
    public List<ActionDefinition> actions() {
        return List.of(
            // 用户管理：读写共用 system:user:manage（码在页面节点上）
            linked("system_users", "user-create", "system_users", USER_MANAGE,
                "新增用户", "新建登录账号并指定所属机构", 10),
            linked("system_users", "user-edit", "system_users", USER_MANAGE,
                "编辑用户", "修改账号资料与状态", 20),
            linked("system_users", "user-toggle-status", "system_users", USER_MANAGE,
                "停用 / 启用", "停用后该账号立即无法登录", 30),
            sensitive("system_users", "user-reset-password", "system_users", USER_MANAGE,
                "重置密码", "重置为初始口令并强制下次登录改密", 40),
            linked("system_users", "user-assign-roles", "system_users", USER_MANAGE,
                "分配角色", "调整该账号拥有的角色", 50),
            sensitive("system_users", "user-import", "system_users", USER_MANAGE,
                "批量导入账号", "按模板整批新建登录账号", 60),
            sensitive("system_users", "user-deregister", "system_users", USER_MANAGE,
                "注销账号", "物理删除账号并解除业务档案归属，不可恢复", 70),

            // 角色管理
            linked("system_roles", "role-create", "system_roles", ROLE_MANAGE,
                "新增角色", "创建角色并配置授权", 10),
            linked("system_roles", "role-edit", "system_roles", ROLE_MANAGE,
                "编辑角色", "修改角色名称、状态与备注", 20),
            linked("system_roles", "role-toggle-status", "system_roles", ROLE_MANAGE,
                "停用 / 启用", "停用后持有该角色的账号失去其权限", 30),
            linked("system_roles", "role-assign-menus", "system_roles", ROLE_MANAGE,
                "授权页面与按钮", "调整该角色能进入哪些页面、能做哪些操作", 40),

            // 菜单管理
            linked("system_menus", "menu-create", "system_menus", MENU_MANAGE,
                "新增菜单", "创建目录、页面或按钮节点", 10),
            linked("system_menus", "menu-edit", "system_menus", MENU_MANAGE,
                "编辑菜单", "修改节点名称、路径与权限码", 20),
            linked("system_menus", "menu-delete", "system_menus", MENU_MANAGE,
                "删除菜单", "删除节点；有子节点时拒绝", 30),

            // 字典管理
            linked("system_dicts", "dict-type-create", "system_dicts", "system:dict:manage",
                "新增字典类型", "创建一类可维护的枚举", 10),
            linked("system_dicts", "dict-type-edit", "system_dicts", "system:dict:manage",
                "编辑字典类型", "修改类型名称与状态", 20),
            linked("system_dicts", "dict-type-delete", "system_dicts", "system:dict:manage",
                "删除字典类型", "删除类型及其条目", 30),
            linked("system_dicts", "dict-item-create", "system_dicts", "system:dict:manage",
                "新增字典条目", "为字典类型增加可选值", 40),
            linked("system_dicts", "dict-item-edit", "system_dicts", "system:dict:manage",
                "编辑字典条目", "修改条目名称与状态", 50),
            linked("system_dicts", "dict-item-delete", "system_dicts", "system:dict:manage",
                "删除字典条目", "删除可选值", 60),

            // 系统参数：页面挂查看码，写动作挂在「参数维护」按钮节点
            linked("system_config", "config-create", "system_config_write", "system:config:write",
                "新增参数", "新增一条可运行时调整的系统参数", 10),
            linked("system_config", "config-edit", "system_config_write", "system:config:write",
                "编辑参数", "修改参数值", 20),
            linked("system_config", "config-delete", "system_config_write", "system:config:write",
                "删除参数", "系统内置参数禁止删除", 30),

            // 列表配置
            linked("system_list_configs", "list-config-create", "system_list_configs",
                "system:list-config:manage", "新增列表配置", "为资源定义列表列、查询条件与按钮", 10),
            linked("system_list_configs", "list-config-edit", "system_list_configs",
                "system:list-config:manage", "编辑列表配置", "修改列展示与按钮配置", 20),
            linked("system_list_configs", "list-config-copy", "system_list_configs",
                "system:list-config:manage", "复制来源配置", "从其他资源复制一份配置作为起点", 30),
            linked("system_list_configs", "list-config-delete", "system_list_configs",
                "system:list-config:manage", "删除列表配置", "删除后回退默认展示", 40),

            // 机构资料：页面挂查看码，写动作挂在「机构资料维护」按钮节点
            linked("system_institution", "institution-save", "system_institution_edit", "tenant:write",
                "保存机构资料", "修改本机构名称、联系方式等基础资料", 10),

            // 数据源配置
            linked("system_data_sources", "data-source-create", "system_data_sources",
                "system:data-source:manage", "新增数据源", "配置下拉选项的数据来源", 10),
            linked("system_data_sources", "data-source-edit", "system_data_sources",
                "system:data-source:manage", "编辑数据源", "修改数据源的查询定义", 20),
            linked("system_data_sources", "data-source-delete", "system_data_sources",
                "system:data-source:manage", "删除数据源", "删除后引用它的下拉失效", 30),

            // 注册凭证
            linked("system_invitations", "invitation-create", "system_invitations",
                "system:invitation:manage", "创建邀请码", "生成供新账号自助注册的邀请码", 10),
            linked("system_invitations", "activation-create", "system_invitations",
                "system:invitation:manage", "生成激活码", "为预建账号生成自设密码的激活码", 20),
            linked("system_invitations", "invitation-disable", "system_invitations",
                "system:invitation:manage", "停用凭证", "停用后该码不能再被使用", 30),

            // 编号规则
            linked("system_code_rules", "code-rule-edit", "system_code_rules",
                "system:code-rule:manage", "编辑编号规则", "修改资源的编号前缀、日期段与流水位数", 10),

            // 导航编排
            linked("system_navigation", "nav-save", "system_navigation", "system:nav:manage",
                "保存导航", "调整分组名称、图标、顺序与所属分组", 10),
            linked("system_navigation", "nav-create-group", "system_navigation", "system:nav:manage",
                "新建分组", "新建侧边栏分组并自动授权给在职角色", 20)
        );
    }

    private ActionDefinition linked(String pageMenuCode, String actionKey, String grantMenuCode,
                                    String permissionCode, String label, String description, int sortNo) {
        return new ActionDefinition(pageMenuCode, actionKey, grantMenuCode, permissionCode,
            label, description, ActionMode.LINKED, false, sortNo);
    }

    private ActionDefinition sensitive(String pageMenuCode, String actionKey, String grantMenuCode,
                                       String permissionCode, String label, String description, int sortNo) {
        return new ActionDefinition(pageMenuCode, actionKey, grantMenuCode, permissionCode,
            label, description, ActionMode.LINKED, true, sortNo);
    }
}
