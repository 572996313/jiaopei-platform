package com.jiaopei.platform.system.service;

import com.jiaopei.platform.system.dto.PermissionOverviewView;

public interface PermissionOverviewService {

    /**
     * 聚合功能权限总览:注解扫描的接口注册表 + 角色持有关系 + 菜单按钮节点 + 列表按钮配置。
     */
    PermissionOverviewView overview();
}
