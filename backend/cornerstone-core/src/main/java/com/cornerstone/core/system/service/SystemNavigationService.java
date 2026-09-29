package com.cornerstone.core.system.service;

import com.cornerstone.core.system.dto.NavigationCatalogCreateRequest;
import com.cornerstone.core.system.dto.NavigationCatalogCreateResultView;
import com.cornerstone.core.system.dto.NavigationUpdateRequest;
import com.cornerstone.core.system.dto.NavigationView;
import java.util.List;

/**
 * 导航编排：把侧边栏的"分组与顺序"开放给客户自己配，接口只暴露这五件事
 * ——组名、图标、组间/组内顺序、页面归属哪个组、是否显示。
 *
 * 与 SystemMenuService 的关系：那张表是菜单的完整事实源，本页是它的一个**受限视图**。
 * 路由、权限码、类型、编码一律不改，因为改这些会把入口指到不存在的页面或把人挡在 403，
 * 那属「菜单管理」的职责，不在这页开放。
 */
public interface SystemNavigationService {

    /** 当前可编排的导航结构（管理端的目录与页面节点，含每个节点已授权的角色数） */
    List<NavigationView> listNavigation();

    /** 批量保存五列改动；整批校验通过才落库 */
    List<NavigationView> saveNavigation(NavigationUpdateRequest request);

    /** 新建顶层分组，并同批授给所有在职角色（漏授权的表现是那组页面的入口整块不显示且不报错） */
    NavigationCatalogCreateResultView createCatalog(NavigationCatalogCreateRequest request);
}
