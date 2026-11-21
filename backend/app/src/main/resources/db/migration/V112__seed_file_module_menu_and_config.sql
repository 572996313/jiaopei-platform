-- ============================================================================
-- 文件管理：菜单节点、角色授权与三条系统参数。
--
-- 双轨：本文件是教培侧真正执行的那份；底座基线侧内容一字不差，见
--   backend/cornerstone-core/src/main/resources/cornerstone/migration/V116__seed_file_module_menu_and_config.sql
--
-- 菜单 id 148/149：管理端段 1~25、101~123、130~147 已占用，200~215 是小程序端段。
-- 授权口径：
--   - 148「文件管理」页面节点（system:file:manage）：只授平台管理员(1)与机构运营(110)；
--     110 同时补父目录 13，否则子页会被 buildTree 提升成顶层散页。
--   - 149「文件上传」按钮节点（system:file:upload）：授给全部在职角色——业务表单的上传
--     按钮都在各业务页里，漏授就是"整站上传 403"。
-- ============================================================================

INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (148, 'admin', 13, 'system_file', '文件管理', 'menu', '/system/files', 'system/files',
     'system:file:manage', 'FolderOpened', 60, 1, 'active',
     '通用文件管理：查看/下载/删除未绑定业务的文件；已被业务引用的文件不允许直接删除', 0),
    (149, 'admin', 13, 'system_file_upload', '文件上传', 'button', NULL, NULL,
     'system:file:upload', NULL, 10, 0, 'active',
     '业务表单上传文件的写权限；勾选后业务页面的上传入口才可用', 0);

-- 页面节点：平台管理员 + 机构运营
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (1480001, 1, 148, 0),
    (1480110, 110, 148, 0);

-- 机构运营补父目录 13（只补目录节点，不授该目录下的账号/角色/菜单等页面）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (1489110, 110, 13, 0);

-- 上传按钮：全部在职角色
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 1490000 + r.id, r.id, 149, 0
FROM sys_role r
WHERE r.deleted = 0
  AND r.status = 'active';

-- 三条运行时参数（系统参数页可改）
INSERT IGNORE INTO sys_system_config (
    id, config_key, config_value, value_type, config_group, display_name, description,
    default_value, is_system, sort_order, status, deleted
) VALUES
    (16, 'file.upload.max_size_mb', '20', 'int', 'system', '文件上传大小上限(MB)',
     '单个文件允许的最大体积；容器硬顶为 50MB，此处只能更小。留空或 0 表示不限制', '20', 0, 10, 'active', 0),
    (17, 'file.upload.allowed_extensions',
     'jpg,jpeg,png,webp,gif,bmp,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,csv,zip,rar',
     'string', 'system', '允许上传的文件类型',
     '逗号分隔的扩展名(小写、不含点)；留空表示不限制', 'jpg,jpeg,png,webp,gif,bmp,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,csv,zip,rar', 0, 20, 'active', 0),
    (18, 'file.orphan.retention_hours', '24', 'int', 'system', '未绑定文件的保留时长(小时)',
     '上传后未绑定业务数据的文件超过该时长会被清理；配 0 或负数停止自动清理', '24', 0, 30, 'active', 0);
