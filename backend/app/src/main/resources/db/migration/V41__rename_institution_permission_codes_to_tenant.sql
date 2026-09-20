-- V41: 机构（租户）权限码改用底座自己的 tenant:* 前缀
-- 机构=租户是底座概念，接口权限串却借用了业务模块的 organization 前缀：第二个项目只装
-- cornerstone-core，却要授予一个"组织模块"的权限码，语义不成立。
-- 本次改的是底座两个机构接口（/api/organizations/institutions、/api/system/institution/current）
-- 对应的码：organization:read|write → tenant:read|write。
-- 业务模块 module-organization（校区/教室/学期/时间段）继续用 organization:*，不在本次范围。

UPDATE sys_menu
SET permission_code = 'tenant:read',
    menu_name = '机构资料查看',
    remark = '机构(租户)资料页可见性与读取所需权限'
WHERE id = 108 AND menu_code = 'system_institution_view';

-- 更新当前机构信息的写接口此前没有对应按钮节点，总览页有码无处勾，补一条。
-- 用 INSERT IGNORE：本地重放（改脚本后删历史行重跑）不该因主键已存在而中断。
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES (
    114, 'admin', 8, 'system_institution_edit', '机构资料维护', 'button', NULL, NULL,
    'tenant:write', NULL, 20, 0, 'active', '编辑机构资料所需权限', 0
);

-- 平台管理员补上这个新节点的授权，否则菜单树里勾不到、与其他按钮不对齐
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES (2100, 1, 114, 0);

-- 平台管理员角色的初始权限串裁净：只留底座码（tenant:* 与 system:*）。
-- 教培业务码由 V42 在业务段补回；第二个项目不带 V42，拿到的就是这串干净种子。
UPDATE sys_role SET permission_codes =
    'tenant:read,tenant:write,system:user:manage,system:role:manage,system:menu:manage,system:dict:manage,system:config:read,system:config:write,system:list-config:manage,system:operlog:read,system:data-source:manage'
WHERE role_code = 'PLATFORM_ADMIN';
