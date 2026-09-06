# 按钮权限模型改版 · 第一期（system 域示范）

## 目标模型

- **权限判定改双轨**：角色生效权限 = 角色行 CSV 权限码（存量兼容）∪ 角色勾选的菜单/按钮节点的 `permission_code`。
- **菜单节点（catalog/menu）只管可见性**：`permission_code` 置空，勾了菜单就看得见页面；**按钮节点（menu_type=button，visible=0）承载接口权限码**，勾了按钮才有接口权限，按钮的中文 `menu_name` 就是客户看到的权限名。
- **前端按钮随权限显隐**：无对应权限码时隐藏新增/编辑/删除等操作按钮（点不到，而不是点了报错）。
- **菜单管理 tab 从只读变可维护**：可新增/编辑/删除菜单与按钮节点。

粒度说明：本期按钮节点与现有 10 个粗粒度码 1:1（system 域为：用户维护、角色维护、菜单维护、字典维护、参数查看、参数维护 6 个按钮）。把「新增用户 vs 重置密码」拆成更细的码需要改后端注解与接口，归入后续批次。

## 后端改动

1. **V16 迁移** `backend/src/main/resources/db/migration/V16__seed_button_permissions.sql`
   - 种 6 个按钮节点（id 101-106，visible=0）：用户维护/角色维护/菜单维护/字典维护挂父菜单 2-5，参数查看/参数维护挂父菜单 6。
   - `sys_role_menu` 给 PLATFORM_ADMIN（role 1）授权这 6 个节点（显式 id 7-12，INSERT IGNORE；表无自增）。
   - 菜单节点 2-6 的 `permission_code` 置 NULL（可见性改由勾选驱动）；顺带清掉 V14 死码 `system:config:manage`。
2. **SystemMenuService** 新增 `getPermissionCodesByRoleIds(List<Long>)`：按 roleId 查 `sys_role_menu` → 批量查菜单 → 过滤 active 且权限码非空，返回码集合。
3. **AuthApplicationServiceImpl.loadRolePermissionSnapshot**：在现有 CSV 码基础上并集上述方法结果（照 `SystemConfigService` 的先例注入 system 域 service）。
   - 注：权限调整后需重新登录生效（token 快照缓存于 Redis，与现状一致），在计划说明中告知客户。

## 前端改动

1. **`frontend/src/constants/permissionLabels.ts`**：10 个码 → 中文名 + 域分组（系统管理/组织/教务），作为码值兜底映射。
2. **`frontend/src/api/system.ts`**：菜单 CRUD 封装（createSystemMenu/updateSystemMenu/deleteSystemMenu），对接已有 `/system/menus` 接口。
3. **`frontend/src/views/system/SystemManagementView.vue`**：
   - 所有操作按钮按 `auth.hasPermission` 显隐：用户 tab（新增/编辑/启停/重置密码/分配角色 → `system:user:manage`）、角色 tab（新增/编辑/启停 → `system:role:manage`；分配菜单 → `system:menu:manage`，与后端接口要求一致）、参数 tab（新增/编辑/删除 → `system:config:write`）、机构 tab（保存 → `organization:write`）。
   - 角色列表「权限码」列显示中文标签（tooltip 显示原始码）。
   - 角色弹窗预设权限按域分组展示。
   - 菜单 tab 增加新增/编辑/删除：弹窗字段含父节点、类型（目录/菜单/按钮）、编码、名称、路径、权限码（下拉选已知码+可手输）、排序、可见、状态、备注。
   - 「分配菜单」树自动包含按钮节点（`tree()` 已返回全部节点，无需后端改动）。

## 明确不做（后续批次）

业务域（教师/学员/班级等 49 处注解）按钮节点、user/role 码细分、CSV 字段废弃与存量迁移、字典 tab 的维护功能。

## 验证

- 后端：`mvnw compile`（如可则跑测试）。
- 前端：`vue-tsc -b` + `vite build`。
- 人工链路：PLATFORM_ADMIN 登录 → 分配菜单树出现按钮节点 → 新建测试角色只勾「用户管理」菜单不勾「用户维护」按钮 → 该角色用户 tab 可见但按钮隐藏、接口 403；勾上按钮并重新登录后恢复。