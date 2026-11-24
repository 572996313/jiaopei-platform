-- ============================================================================
-- 数据字典：菜单节点与角色授权（底座基线）。
--
-- 双轨：本文件供"下游从零建库"使用；教培侧真正执行的那份内容一字不差，见
--   backend/app/src/main/resources/db/migration/V116__seed_schema_dictionary_menu.sql
--
-- 菜单 id 150：管理端段 1~25、101~123、130~149 已占用，200~215 是小程序端段。
-- 授权口径（照 V116 文件管理）：
--   - 150「数据字典」页面节点（system:schema:read）：只授平台管理员(1)与机构运营(110)；
--     110 同时补父目录 13（V116 已补过一次，INSERT IGNORE 幂等），
--     否则子页会被 buildTree 提升成顶层散页。
-- 读权限由菜单节点携带：UserAccessTokenService 会把已授权菜单节点的 permission_code
-- 合并进权限快照，因此不必再 UPDATE sys_role.permission_codes。
-- ============================================================================

INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (150, 'admin', 13, 'system_schema_dictionary', '数据字典', 'menu', '/system/schema-dictionary',
     'system/schema-dictionary', 'system:schema:read', 'Tickets', 70, 1, 'active',
     '库表结构与字段含义的只读视图；枚举值在「字典管理」，不在这里。结构只读，改结构一律走 Flyway', 0);

-- 页面节点：平台管理员 + 机构运营
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (1500001, 1, 150, 0),
    (1500110, 110, 150, 0);

-- 机构运营补父目录 13（只补目录节点，不授该目录下的其他页面）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (1509110, 110, 13, 0);
