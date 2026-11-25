-- ============================================================================
-- 业务链路：菜单节点与角色授权。
--
-- 双轨：本文件是教培侧真正执行的那份；底座基线侧内容一字不差，见
--   backend/cornerstone-core/src/main/resources/cornerstone/migration/V121__seed_document_lineage_menu.sql
--
-- 菜单 id 152：接在接口链路 151 之后，同属「系统设置」目录 13。
-- 授权口径（照 V116/V118）：只授平台管理员(1)与机构运营(110)；
-- 110 同时补父目录 13（INSERT IGNORE 幂等），否则子页会被 buildTree 提升成顶层散页。
-- 读权限由菜单节点携带：UserAccessTokenService 会把已授权菜单节点的 permission_code
-- 合并进权限快照，因此不必再 UPDATE sys_role.permission_codes。
-- ============================================================================

INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (152, 'admin', 13, 'system_document_lineage', '业务链路', 'menu', '/system/document-lineage',
     'system/document-lineage', 'system:lineage:read', 'Connection', 72, 1, 'active',
     '按单据查看生成、汇总、核销关系；关系是代码里的声明，不代表某次请求的数据流', 0);

-- 页面节点：平台管理员 + 机构运营
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (1520001, 1, 152, 0),
    (1520110, 110, 152, 0);

-- 机构运营补父目录 13（只补目录节点，不授该目录下的其他页面）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (1529110, 110, 13, 0);
