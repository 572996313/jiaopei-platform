-- ============================================================================
-- 接口链路：菜单节点与角色授权。
--
-- 双轨：本文件是底座基线侧的那份；教培侧真正执行的文件内容一字不差，见
--   backend/app/src/main/resources/db/migration/V118__seed_endpoint_chain_menu.sql
--
-- 菜单 id 151：接在数据字典 150 之后，同属「系统设置」目录 13。
-- 授权口径（照 V116 数据字典）：
--   - 151「接口链路」页面节点（system:endpoint:read）：只授平台管理员(1)与机构运营(110)；
--     110 同时补父目录 13（INSERT IGNORE 幂等），否则子页会被 buildTree 提升成顶层散页。
-- 读权限由菜单节点携带：UserAccessTokenService 会把已授权菜单节点的 permission_code
-- 合并进权限快照，因此不必再 UPDATE sys_role.permission_codes。
-- ============================================================================

INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (151, 'admin', 13, 'system_endpoint_chain', '接口链路', 'menu', '/system/endpoint-chain',
     'system/endpoint-chain', 'system:endpoint:read', 'Share', 71, 1, 'active',
     '按接口查看权限码、界面按钮、控制器、服务与数据表；链路是声明依赖，不代表某次请求的实际调用', 0);

-- 页面节点：平台管理员 + 机构运营
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (1510001, 1, 151, 0),
    (1510110, 110, 151, 0);

-- 机构运营补父目录 13（只补目录节点，不授该目录下的其他页面）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (1519110, 110, 13, 0);
