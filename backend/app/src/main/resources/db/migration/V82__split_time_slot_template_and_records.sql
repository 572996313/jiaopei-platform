-- V82: 「节次与时间模板」一页拆成两页——模板页只留模板，节次清单独立成「节次明细」
--
-- 背景：V81 把原 /campus 里的时间段表并进了 /time-slots，结果那一页变成
--   筛选 → 节次参数 → 节次网格 → 节次清单 四段，纵向约 980px，1440×900 一屏放不下，
--   「保存」按钮被顶到第二屏（客户现场有人的鼠标滚轮是坏的，反馈很不好）。
-- 参数与网格是"改一套节次"的连续动作，清单是"查全部节次"的另一件事，不该叠在同一屏。
-- 现在按职责拆开：
--   /time-slots       时段模板（参数 + 网格 + 整批保存，模板页顶上一颗「节次明细」按钮跳过去）
--   /time-slot-list   节次明细（本校区 + 机构通用的逐条新增/编辑/删除）
-- 后端接口一个字没改，只是取数入口从一页挪到两页。
--
-- id 避让：菜单 132（现有号段 1~25、101~129、130/131、200~215，已按 SELECT id>=130 核对 132 空闲）；
-- sys_role_menu 用 1320000 + 角色 ID 取号，与 V81 的 1300000/1310000 同一档、不与之重叠。
--
-- 挂码口径（见 AGENTS「权限码与菜单节点的分配规则」）：
--   1) 新页面节点 132 自带 organization:read——读码挂页面节点，勾页面 = 入口可见 + 能读。
--   2) 不再给节次明细配第二个 organization:write 按钮节点：同一个码配两个节点只是让操作员
--      以为"勾一个不够"，116 那一条继续覆盖这几张组织档案页面的写操作。
--   3) 两页互跳，授权要一起给：只勾 21 不勾 132 的人点「节次明细」会进不去（反之只勾 132
--      的人看不到模板页），所以 132 跟着"原本能看到 21"的角色批量补。

-- 1) 原页面收窄成"只管模板"，名字跟着改（页面上已经没有节次清单了）
UPDATE sys_menu
SET menu_name = '时段模板',
    sort_no = 30,
    remark = '时段模板的参数与节次网格，整批保存；逐条维护节次去「节次明细」；勾选即授组织查看码'
WHERE id = 21 AND menu_code = 'organization_time_slots';

-- 2) 新页面：节次明细（校区级 + 机构通用，从模板页并过来的那张表）
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (132, 'admin', 130, 'organization_time_slot_list', '节次明细', 'menu', '/time-slot-list',
     'organization/TimeSlotListView',
     'organization:read', 'AlarmClock', 40, 1, 'active',
     '本校区与机构通用的节次逐条维护；勾选即授组织查看码', 0);

-- 3) 组织档案维护按钮的覆盖范围说明（码没变，还是那一条 organization:write）
UPDATE sys_menu
SET remark = '校区与学期/教室管理/时段模板/节次明细四张页面的写操作共用这一个码，勾了即可写'
WHERE id = 116 AND menu_code = 'organization_master_data_write';

-- 4) 节次明细授权：跟着「原本能看到时段模板页」的角色走（两页互跳，缺一个就是死链）
--    DISTINCT 是必要的：一个角色可能有多行指向 21，取号式相同会撞主键
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1320000 + rm.role_id, rm.role_id, 132, 0
FROM sys_role_menu rm
WHERE rm.deleted = 0
  AND rm.menu_id = 21;
