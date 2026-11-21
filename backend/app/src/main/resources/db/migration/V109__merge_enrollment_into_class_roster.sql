-- V109：报名台账（jp_enrollment）并入分班与名册（jp_class_student）
--
-- 起因：报名与入班在业务上从来不是两件事——报名弹窗班级必填、创建报名同事务自动写名册，
--   名册的「新增入班」又不回写报名台账。两张表各带一份 UNIQUE(student_id, class_id)，内容 1:1，
--   名册的关系集合包含报名的，收费/考勤/排课/公告/小程序等所有下游只读名册。报名台账实际只剩
--   「报名编号 + 来源渠道 + 报名日期 + 备注」四个纯记录字段，没有任何业务计算消费它，菜单上却
--   多出一个页面和一张表，操作员要判断"这件事该去报名页还是名册页"。
--
-- 本次口径（2026-11-21 与使用方确认）：
--   1) 报名并入名册：名册行就是报名记录，只补一列「来源渠道」；报名日期即入班日期（joined_at），
--      备注名册本来就有；报名状态 submitted/confirmed/cancelled 没有任何流转接口，整列废弃。
--   2) 彻底不要报名编号：enrollment_no 列、编号资源 enrollment、sys_code_rule 里的 ENR 行一并删除。
--   3) 退班仍是物理删除（沿用现口径）：移除后名册行消失，报名/渠道记录随之消失。这是合并后的
--      既定语义，不再保留"退过班的人"的台账痕迹。
--
-- 存量处理：
--   - 报名与名册按 (class_id, student_id) 一对一，把渠道回填到对应名册行。
--   - 名册行已被物理删除（退过班）的报名行没有承载处，随表一起丢弃——这与第 3 条物理删除口径一致。
--   - 不重建这类行：重建会占住 uk_jp_class_student(class_id, student_id)，导致该学员无法再入同一班，
--     比丢历史更糟。
--
-- 兼容自查：
--   1) 普通 DDL/DML，无 CTE、无窗口函数、无 JSON_TABLE、无 IF( 函数、无 PREPARE。
--   2) jp_enrollment 没有任何入向外键（information_schema 已核），DROP TABLE 不会连带别的表；
--      它自己的三条外键随表删除。
--   3) 菜单按 V62 先例软删（deleted = 1）并清掉 sys_role_menu 授权，不硬删行，重放安全。
--   4) 全脚本 INSERT/UPDATE/DELETE 均按业务键定位，可整脚本重放。

-- 1) 名册补「来源渠道」列（可空：补录入班的老学员可能没有渠道）
ALTER TABLE `jp_class_student`
    ADD COLUMN `channel` VARCHAR(32) NULL
        COMMENT '报名渠道字典(enrollment_channel:front_desk=前台报名,consultant=顾问录入,online=线上线索,referral=转介绍)'
        AFTER `status`;

-- 2) 存量回填：报名与名册按 (class_id, student_id) 对上，把渠道带过去
UPDATE `jp_class_student` cs
JOIN `jp_enrollment` e
  ON e.`class_id` = cs.`class_id`
 AND e.`student_id` = cs.`student_id`
 AND e.`deleted` = 0
SET cs.`channel` = e.`channel`
WHERE cs.`deleted` = 0
  AND cs.`channel` IS NULL;

-- 3) 删除报名台账表
DROP TABLE `jp_enrollment`;

-- 4) 删除报名编号资源：规则行与代码侧登记同批下线，不留"配置页能看到、点生成必报未登记"的悬空项
DELETE FROM `sys_code_rule` WHERE `resource_code` = 'enrollment';

-- 5) 菜单「学员报名」(22) 下线：软删节点并把历史授权清掉，避免分配菜单树里留下勾不到的残留
UPDATE `sys_menu`
SET `deleted` = 1,
    `status` = 'inactive',
    `remark` = 'V109 起报名并入「分班与名册」，本页面下线'
WHERE `id` = 22 AND `menu_code` = 'education_enrollments' AND `deleted` = 0;

DELETE FROM `sys_role_menu` WHERE `menu_id` = 22;

-- 6) 名册页说明改成"报名即入班"的新口径
UPDATE `sys_menu`
SET `remark` = '把学员编进班、退班，看班上都有谁与家长联系方式；新增入班即报名，可同时记录来源渠道；勾选即授教务查看码'
WHERE `id` = 133 AND `menu_code` = 'education_class_roster';
