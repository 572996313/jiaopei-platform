package com.cornerstone.core.system.schema;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 底座自带表的维护入口登记。
 *
 * <p>只登记"有管理页面可改"的底座表；纯关系表指向承载它的页面（如角色授权在角色管理里），
 * 而不是另开入口。业务表的入口由各业务模块的 Contributor 登记。</p>
 */
@Component("coreSchemaMaintenanceContributor")
public class CoreSchemaMaintenanceContributor implements SchemaMaintenanceContributor {

    @Override
    public String moduleKey() {
        return "core";
    }

    @Override
    public List<SchemaMaintenanceEntry> entries() {
        return List.of(
            new SchemaMaintenanceEntry("sys_user_account", "用户管理", "/system/users",
                "登录账号、显示名、状态与强制改密"),
            new SchemaMaintenanceEntry("sys_user_role_rel", "用户管理", "/system/users",
                "账号与角色的绑定关系"),
            new SchemaMaintenanceEntry("sys_role", "角色管理", "/system/roles",
                "角色定义与接口权限码"),
            new SchemaMaintenanceEntry("sys_role_menu", "角色管理", "/system/roles",
                "角色可见的菜单与按钮（授权弹窗）"),
            new SchemaMaintenanceEntry("sys_role_data_scope", "角色管理", "/system/roles",
                "角色可访问的数据范围（范围维度由业务模块定义）"),
            new SchemaMaintenanceEntry("sys_menu", "菜单管理", "/system/menus",
                "侧边栏节点、路由与权限码"),
            new SchemaMaintenanceEntry("sys_dict_type", "字典管理", "/system/dicts",
                "枚举类型清单（状态、性别、关系等）"),
            new SchemaMaintenanceEntry("sys_dict_item", "字典管理", "/system/dicts",
                "枚举值；页面上的下拉选项多数来自这里"),
            new SchemaMaintenanceEntry("sys_system_config", "系统参数", "/system/config",
                "运行时可调参数，保存即生效"),
            new SchemaMaintenanceEntry("sys_list_config", "列表配置", "/system/list-configs",
                "列表页的列/筛选/按钮/弹窗配置"),
            new SchemaMaintenanceEntry("sys_data_source", "数据源", "/system/data-sources",
                "下拉选项的可配置数据来源"),
            new SchemaMaintenanceEntry("sys_code_rule", "编号规则", "/system/code-rules",
                "各类档案编号的生成格式与启用状态"),
            new SchemaMaintenanceEntry("sys_file", "文件管理", "/system/files",
                "上传文件与业务绑定关系"),
            new SchemaMaintenanceEntry("sys_auth_invitation", "邀请码", "/system/invitations",
                "小程序注册邀请码与角色白名单"),
            new SchemaMaintenanceEntry("sys_institution", "机构资料", "/system/institution",
                "本机构名称与联系信息"),
            new SchemaMaintenanceEntry("oper_log", "操作日志", "/system/operlogs",
                "独立日志库 jiaopei_log，不在本库；此处只登记入口")
        );
    }
}
