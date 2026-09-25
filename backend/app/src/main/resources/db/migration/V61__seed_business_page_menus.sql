-- V61: 业务页面进菜单表，让 4 个"已登记但未挂菜单"的权限码有处可勾、导航按授权显隐
-- 背景：organization:read|write、education:read、miniapp:access 四个码后端一直在校验，
-- 但 sys_menu 里没有任何节点承载它们（V41 把唯一挂过 organization:read 的 108 号节点改成了 tenant:read，
-- 组织/教务的五个业务页面则从没进过菜单表），导致：
--   1)「分配菜单」树里勾不到这四个码，只能靠角色的预设权限 CSV；
--   2) 侧边栏这五个入口在前端写死 alwaysVisible，与角色授权无关，
--      没读权限的人照样看得到入口，点进去整页 403。
-- 本脚本按「排课中心」那套做法补齐节点：页面节点自带读取码（勾页面=给读码，不存在"看得见却读不了"的中间态），
-- 写码仍走隐藏按钮节点。注意角色预设权限 CSV 与菜单节点是取并集（UserAccessTokenService），
-- 两条路径任一授予即生效，撤销时要两边都撤。

-- 1) 五个业务页面节点（顶层、可见），各自带本模块读取码
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (22, 'admin', NULL, 'education_enrollments', '报名与入班', 'menu', '/students', 'education/StudentManagementView',
     'education:read', 'User', 50, 1, 'active', '报名与入班页；勾选即授教务查看码', 0),
    (23, 'admin', NULL, 'education_classes', '班级学员', 'menu', '/classes', 'education/ClassManagementView',
     'education:read', 'Reading', 60, 1, 'active', '班级与学员页；勾选即授教务查看码', 0),
    (20, 'admin', NULL, 'organization_campus', '机构与教学资源', 'menu', '/campus', 'organization/CampusManagementView',
     'organization:read', 'OfficeBuilding', 70, 1, 'active', '校区/教室/学期页；勾选即授组织查看码', 0),
    (21, 'admin', NULL, 'organization_time_slots', '时间段设置', 'menu', '/time-slots', 'organization/TimeSlotTemplateView',
     'organization:read', 'Timer', 80, 1, 'active', '节次模板与网格编辑页；勾选即授组织查看码', 0),
    (24, 'admin', NULL, 'education_teachers', '教师档案', 'menu', '/teachers', 'education/TeacherManagementView',
     'education:read', 'Avatar', 90, 1, 'active', '教师档案页；勾选即授教务查看码', 0);

-- 2) 排课中心三个页面节点补教务查看码，与新页面同口径
--    （此前页面节点不带码，角色只要被勾了页面就能进，读取权限全靠预设权限 CSV 另行授予）
UPDATE sys_menu
SET permission_code = 'education:read',
    remark = '排课计划页；勾选即授教务查看码'
WHERE id = 16 AND menu_code = 'education_scheduling';

UPDATE sys_menu
SET permission_code = 'education:read',
    remark = '课次执行与考勤页；勾选即授教务查看码'
WHERE id = 17 AND menu_code = 'education_execution';

UPDATE sys_menu
SET permission_code = 'education:read',
    remark = '排课完成度报表页；勾选即授教务查看码'
WHERE id = 19 AND menu_code = 'education_completeness';

-- 3) 组织档案维护按钮：organization:write 此前无任何节点承载，角色在菜单树里勾不到
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (116, 'admin', 20, 'organization_master_data_write', '组织档案维护', 'button', NULL, NULL,
     'organization:write', NULL, 10, 0, 'active',
     '校区/教室/学期/时间段的写操作共用这一个码，勾了即可写全部组织页面', 0);

-- 4) 小程序端访问按钮：miniapp:access 此前只写在两个小程序角色的预设权限 CSV 里
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (117, 'student', 200, 'student_portal_access', '小程序端访问', 'button', NULL, NULL,
     'miniapp:access', NULL, 90, 0, 'active', '学生端七个 /api/miniapp 接口共用此码', 0),
    (118, 'parent', 210, 'parent_portal_access', '小程序端访问', 'button', NULL, NULL,
     'miniapp:access', NULL, 90, 0, 'active', '家长端七个 /api/miniapp 接口共用此码', 0);

-- 5) 平台管理员补新节点授权，否则菜单树里勾不到、与其他页面对不齐
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (6001, 1, 20, 0),
    (6002, 1, 21, 0),
    (6003, 1, 22, 0),
    (6004, 1, 23, 0),
    (6005, 1, 24, 0),
    (6006, 1, 116, 0),
    (6101, 101, 117, 0),
    (6102, 102, 118, 0);
