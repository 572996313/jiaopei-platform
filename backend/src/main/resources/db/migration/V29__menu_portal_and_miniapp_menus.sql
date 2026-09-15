-- 引入「端(Portal)」维度:学生端(portal=student)/家长端(portal=parent)/平台用户(portal=admin)功能清单各自维护,
-- 现有 admin 菜单默认 portal='admin' 无需改动。小程序端菜单 ID 用 200+ 段避免与后台(1-9,101-109)冲突。

ALTER TABLE sys_menu
    ADD COLUMN portal VARCHAR(32) NOT NULL DEFAULT 'admin' AFTER id,
    ADD INDEX idx_sys_menu_portal (portal);

-- 学生端菜单树
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (200, 'student', NULL, 'student_home', '学生端', 'catalog', '/student', NULL, NULL, 'home', 10, 1, 'active', '学生端根目录', 0),
    (201, 'student', 200, 'student_timetable', '我的课表', 'menu', '/student/timetable', 'timetable', NULL, 'calendar', 10, 1, 'active', '学生查看本人课表', 0),
    (202, 'student', 200, 'student_attendance', '我的考勤', 'menu', '/student/attendance', 'attendance', NULL, 'checkbox', 20, 1, 'active', '学生查看出勤记录', 0),
    (203, 'student', 200, 'student_lessons_left', '课时剩余', 'menu', '/student/lessons', 'lessons-left', NULL, 'wallet', 30, 1, 'active', '学生查看剩余课时', 0);

-- 家长端菜单树
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (210, 'parent', NULL, 'parent_home', '家长端', 'catalog', '/parent', NULL, NULL, 'home', 10, 1, 'active', '家长端根目录', 0),
    (211, 'parent', 210, 'parent_children', '我的孩子', 'menu', '/parent/children', 'children', NULL, 'users', 10, 1, 'active', '家长查看孩子列表', 0),
    (212, 'parent', 210, 'parent_timetable', '课程表', 'menu', '/parent/timetable', 'timetable', NULL, 'calendar', 20, 1, 'active', '家长查看孩子课表', 0),
    (213, 'parent', 210, 'parent_attendance', '考勤记录', 'menu', '/parent/attendance', 'attendance', NULL, 'checkbox', 30, 1, 'active', '家长查看孩子考勤', 0),
    (214, 'parent', 210, 'parent_lessons_left', '课耗明细', 'menu', '/parent/lessons', 'lessons-left', NULL, 'wallet', 40, 1, 'active', '家长查看课时消费', 0),
    (215, 'parent', 210, 'parent_notices', '消息通知', 'menu', '/parent/notices', 'notices', NULL, 'notification', 50, 1, 'active', '调课/停课通知', 0);

-- 学生/家长角色 ↔ 各自端菜单授权
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (200, 101, 200, 0), (201, 101, 201, 0), (202, 101, 202, 0), (203, 101, 203, 0),
    (210, 102, 210, 0), (211, 102, 211, 0), (212, 102, 212, 0), (213, 102, 213, 0),
    (214, 102, 214, 0), (215, 102, 215, 0);