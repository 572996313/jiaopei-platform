-- V113: 业务读码清零——页面可见性只由 sys_role_menu 决定，读取接口登录即可
--
-- 背景：业务页面的读取码（education:read / organization:read / announcement:read）挂在页面节点上，
-- 导致"想给某角色一个功能，就必须把承载该码的基础页面勾进侧边栏"。客户的实际诉求是
-- "不想看机构/班级这些基础页，但其它页面还要能取校区/学期/学员数据"，两边打架。
-- 本批把业务读取码整体撤掉：
--   1) 页面节点不再挂读取码 → SystemMenuServiceImpl#currentUserMenus 退化为纯 sys_role_menu；
--   2) 业务模块的 GET 接口去掉 @RequiresPermission → 登录即可，机构/校区范围仍由
--      InstitutionScopeGuard / CampusScopeGuard 在服务层兜住（不是不做隔离，是换到更合适的层）；
--   3) 写码（education:*:write / organization:write / announcement:write）原样保留，
--      仍由隐藏按钮节点承载，勾了才能写；
--   4) 角色 CSV 里残留的三个读码一并清掉，避免「功能权限总览」把它们报成"未登记码"。
--
-- 注意：读取接口没有码之后，页面隐藏只挡侧边栏，直输 URL 仍能读到数据（前端路由没有菜单守卫）。
-- 需要"连读都不给"的页面只能靠机构/校区范围或后续单独加码，不能再靠不勾页面。

-- 1) 业务页面节点：去掉读取码（目录节点与写按钮不动）
UPDATE sys_menu
SET permission_code = NULL,
    remark = LEFT(CONCAT(IFNULL(remark, ''), '；读取改为登录即可，页面可见性只看 sys_role_menu'), 500)
WHERE deleted = 0
  AND portal = 'admin'
  AND menu_type = 'menu'
  AND permission_code IN ('education:read', 'organization:read', 'announcement:read');

-- 2) 角色 CSV 清掉三个读码（前后补逗号后按 ",code," 整段替换，再修剪首尾逗号）
UPDATE sys_role
SET permission_codes = TRIM(BOTH ',' FROM REPLACE(REPLACE(REPLACE(
        CONCAT(',', IFNULL(permission_codes, ''), ','),
        ',education:read,', ','),
        ',organization:read,', ','),
        ',announcement:read,', ','))
WHERE deleted = 0
  AND (permission_codes LIKE '%education:read%'
    OR permission_codes LIKE '%organization:read%'
    OR permission_codes LIKE '%announcement:read%');
