package com.jiaopei.platform.system.service;

import com.jiaopei.platform.system.dto.PermissionOptionView;
import com.jiaopei.platform.system.dto.PermissionOverviewView;

import java.util.List;

public interface PermissionOverviewService {

    /**
     * 聚合功能权限总览:注解扫描的接口注册表 + 角色持有关系 + 菜单按钮节点 + 列表按钮配置。
     */
    PermissionOverviewView overview();

    /**
     * 全量可分配权限码选项(角色弹窗「预设权限」/菜单按钮 permissionCode 下拉/列表权限标签回显的公共数据源)。
     * 以 {@code @RequiresPermission} 注解扫描注册表为权威源,合并启用按钮节点的中文名;
     * 无按钮节点的码(如 education:read、miniapp:access)label 回退为权限码本身。
     */
    List<PermissionOptionView> permissionOptions();
}
