-- V92: 业务菜单收成四个组，散落在顶层的七张页面归进目录（客户反馈"菜单一堆、不知道点哪个"）
--
-- 背景：V81~V87 为免滚轮把一页拆成几页，每拆一页就往顶层加一个叶子节点，结果顶层 13 项里有 8 项
-- 是散页、只有 3 个目录，图标导航下侧边栏一次铺 12 个入口；又因为组名是"日常教务/班级与教学/档案中心"
-- 这种对象分组，同一条链（学员→报名→班级→进班）被切进两个组，操作员预判不出功能在哪一组。
-- 本次只动结构，不动页面本身：
--   排课与上课(15)   排课管理 16 / 课表与点名 17 / 课时完成度 19
--   学员与班级(139)  学员档案 134 / 学员报名 22 / 班级档案 23 / 分班与名册 133   ← 新建目录
--   基础资料(130)    教师档案 24 / 课程档案 135 / 班型档案 137 / 校区与学期 20 / 教室管理 131
--                    / 时段模板 21 / 节次明细 132 / 校区默认账号 136
--   公告中心(25)     仍是单页
-- 组顺序按"多久用一次"排：每天上课点名在前，每周排课与招生居中，开班前配好就不常进的档案垫底。
--
-- 不变的部分：id、menu_code、permission_code、按钮节点的父级归属一个字都没改，所以已授权的角色关系
-- 与接口全部照旧，不需要给任何角色补权限，也不用踢人重登（读的是页面节点，写的是按钮节点）。
--
-- id 避让：菜单 139（130~138 已用，139~199 空闲，已按 SELECT id>=139 AND id<200 核对）；
-- sys_role_menu 取号 1390000/1392000/1393000 + 角色 ID（V81~V87 用的是 1300000~1370000 段、
-- V90 用 1380001，本段整段空闲，已按 MAX(id) 核对）。仍然只加不乘：角色 id 有 8e18 量级的造数角色。
--
-- 必须一起做的一件事：目录节点 139 要授给**所有**在职角色，还要给"勾了子页但没勾父目录"的角色补 15/130。
-- 菜单树是按角色已授权节点拼的，子页的父目录没被勾到时会被当孤儿提升到根，而侧边栏的组靠
-- menuCatalogCode 找目录节点取子项——目录没授权 = 组内页面入口整块消失（不是少一个分组，是点不进去，
-- 也不报错）。生产库里现场自建角色只勾页面不勾目录很常见，所以第 9~11 段三条是兜底，不是走过场。
-- 目录自己不带权限码，且"没有可见子节点的目录不渲染"，所以全量授目录不会多出空入口（同 V81）。
--
-- 兼容自查：普通 DML，一条语句一条路径；无 CTE、无窗口函数、无 JSON_TABLE、无 IF( 函数、无 PREPARE；
-- 不建表不改列，所以不涉及排序规则漂移；INSERT IGNORE + 按 id 定位的 UPDATE，可整脚本重放。

-- 1) 新建「学员与班级」目录（catalog 不挂权限码）
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (139, 'admin', NULL, 'education_student_class', '学员与班级', 'catalog', NULL, NULL,
     NULL, 'School', 30, 1, 'active',
     '从建档到进班：学员档案 → 学员报名 → 班级档案 → 分班与名册；目录不挂权限码，入口显隐由子页面节点决定', 0);

-- 2) 两个已有目录改名并把组序定下来（名称即侧边栏组名、横幅面包屑的来路）
UPDATE sys_menu
SET menu_name = '排课与上课',
    sort_no = 20,
    remark = '每天的课与每周的计划都在这组：排课管理 → 课表与点名 → 课时完成度；目录不挂权限码'
WHERE id = 15 AND menu_code = 'education_schedule_center';

UPDATE sys_menu
SET menu_name = '基础资料',
    sort_no = 40,
    remark = '开班前要配好的档案，配好就不常进：教师、课程、班型、校区与学期、教室、节次；目录不挂权限码'
WHERE id = 130 AND menu_code = 'organization_teaching_resources';

-- 3) 学员与班级四张页面进目录，顺序按业务链走；班级名册改名带动作
UPDATE sys_menu
SET parent_id = 139, sort_no = 10,
    remark = '新增学员与家长联系人；登记报名去「学员报名」，办理进班去「分班与名册」；勾选即授教务查看码'
WHERE id = 134 AND menu_code = 'education_students';

UPDATE sys_menu
SET parent_id = 139, sort_no = 20,
    remark = '登记报名与缴费状态；学员本身的档案在「学员档案」，办理进班在「分班与名册」；勾选即授教务查看码'
WHERE id = 22 AND menu_code = 'education_enrollments';

UPDATE sys_menu
SET parent_id = 139, sort_no = 30,
    remark = '建班与班级设置、任课教师、开班风险；把学员编进班在「分班与名册」；勾选即授教务查看码'
WHERE id = 23 AND menu_code = 'education_classes';

UPDATE sys_menu
SET parent_id = 139, sort_no = 40, menu_name = '分班与名册',
    remark = '把学员编进班、退班，看班上都有谁与家长联系方式；勾选即授教务查看码'
WHERE id = 133 AND menu_code = 'education_class_roster';

-- 4) 教师与课程两张档案页并进「基础资料」，让常改的排课/学员页在前、这些垫底
UPDATE sys_menu
SET parent_id = 130, sort_no = 10,
    remark = '教师主档、绑登录账号、批量导入；勾选即授教务查看码'
WHERE id = 24 AND menu_code = 'education_teachers';

UPDATE sys_menu
SET parent_id = 130, sort_no = 20,
    remark = '课程与课程分类；班型在「班型档案」，班级怎么搭在「班级档案」；勾选即授教务查看码'
WHERE id = 135 AND menu_code = 'education_courses';

UPDATE sys_menu
SET parent_id = 130, sort_no = 30,
    remark = '班型编码、名称、授课方式与人数区间；建班级时在「班级档案」里选；勾选即授教务查看码'
WHERE id = 137 AND menu_code = 'education_class_types';

-- 5) 目录内原有五张页面往后顺延（教师与课程挪到前面）
UPDATE sys_menu SET sort_no = 40,
    remark = '建校区、维护学期与校历；勾选即授组织查看码'
WHERE id = 20 AND menu_code = 'organization_campus';

UPDATE sys_menu SET sort_no = 50,
    remark = '按校区维护教室与容量；勾选即授组织查看码'
WHERE id = 131 AND menu_code = 'organization_classrooms';

UPDATE sys_menu SET sort_no = 60,
    remark = '一整套节次的时间安排，建班级时套用；逐条改节次去「节次明细」；勾选即授组织查看码'
WHERE id = 21 AND menu_code = 'organization_time_slots';

UPDATE sys_menu SET sort_no = 70,
    remark = '每天第几节、几点到几点，一条一节地维护；勾选即授组织查看码'
WHERE id = 132 AND menu_code = 'organization_time_slot_list';

UPDATE sys_menu SET sort_no = 80,
    remark = '账号默认落在哪个校区，决定登录后看到的范围；勾选即授组织查看码'
WHERE id = 136 AND menu_code = 'organization_campus_accounts';

-- 6) 三张页面的名字改成带动作的说法（横幅大标题、页签名、面包屑都取这里）
UPDATE sys_menu
SET menu_name = '课表与点名',
    remark = '今天的课、点名与调课，日/周课表也在这页；勾选即授教务查看码'
WHERE id = 17 AND menu_code = 'education_execution';

UPDATE sys_menu
SET menu_name = '课时完成度',
    remark = '每班应排、已排、已上，找缺口发起补排；勾选即授教务查看码'
WHERE id = 19 AND menu_code = 'education_completeness';

UPDATE sys_menu
SET remark = '生成与调整课表：周期计划、排课规则、一键补排；勾选即授教务查看码'
WHERE id = 16 AND menu_code = 'education_scheduling';

-- 7) 公告中心排到业务组末尾
UPDATE sys_menu
SET sort_no = 50,
    remark = '给教师与家长发通知，看送达与已读；勾选即授公告查看码'
WHERE id = 25 AND menu_code = 'announcement_center';

-- 8) 目录 139 授给所有在职角色（缺了它，学员与班级四张页面的入口整块不显示，见头部说明）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 1390000 + r.id, r.id, 139, 0
FROM sys_role r
WHERE r.deleted = 0;

-- 9) 兜底：勾了「基础资料」里的页面却没勾目录 130 的角色，补上目录
--    DISTINCT 必要：一个角色勾了两张子页就会出两行，取号式相同会撞主键
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1392000 + rm.role_id, rm.role_id, 130, 0
FROM sys_role_menu rm
WHERE rm.deleted = 0
  AND rm.menu_id IN (20, 21, 24, 131, 132, 135, 136, 137);

-- 10) 兜底：同上，针对「排课与上课」目录 15
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1393000 + rm.role_id, rm.role_id, 15, 0
FROM sys_role_menu rm
WHERE rm.deleted = 0
  AND rm.menu_id IN (16, 17, 19);
