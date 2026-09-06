-- V16: 将种子数据中面向用户的英文菜单/字典文案统一汉化为中文
-- 说明：只改 menu_name / remark / type_name / item_name / remark 等展示字段，
-- 不改动 menu_code、type_code、item_code 等技术编码。

-- 系统菜单汉化
UPDATE sys_menu
SET menu_name = CASE menu_code
        WHEN 'system' THEN '系统管理'
        WHEN 'system_users' THEN '用户管理'
        WHEN 'system_roles' THEN '角色管理'
        WHEN 'system_menus' THEN '菜单管理'
        WHEN 'system_dicts' THEN '字典管理'
        ELSE menu_name
    END,
    remark = CASE remark
        WHEN 'System root menu' THEN '系统管理根菜单'
        WHEN 'User management' THEN '用户管理菜单'
        WHEN 'Role management' THEN '角色管理菜单'
        WHEN 'Menu management' THEN '菜单管理菜单'
        WHEN 'Dictionary management' THEN '字典管理菜单'
        ELSE remark
    END
WHERE deleted = 0;

-- 字典类型与条目汉化
UPDATE sys_dict_type
SET type_name = '通用状态',
    remark = '通用的启用/停用状态'
WHERE type_code = 'common_status'
  AND deleted = 0;

UPDATE sys_dict_item
SET item_name = CASE item_code
        WHEN 'active' THEN '启用'
        WHEN 'disabled' THEN '停用'
        ELSE item_name
    END,
    remark = CASE remark
        WHEN 'Active status' THEN '启用状态'
        WHEN 'Disabled status' THEN '停用状态'
        ELSE remark
    END
WHERE deleted = 0;

-- 种子机构与账号备注汉化
UPDATE jp_institution
SET remark = '首个管理员账号使用的初始机构'
WHERE remark = 'Initial institution for first admin account'
  AND deleted = 0;

UPDATE jp_user_account
SET remark = '初始管理员账号，首次登录后请修改密码'
WHERE remark = 'Initial admin account, change password after first login'
  AND deleted = 0;

UPDATE jp_role
SET remark = '初始平台管理员角色'
WHERE remark = 'Initial platform admin role'
  AND deleted = 0;
