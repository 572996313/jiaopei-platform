-- 排课自动化的两个输入：班级「每周应上几节」与教师「每周/每天最多几节」。
--
-- 为什么放这两张表而不是排课计划上：
--   1) weekly_lessons 是班级属性，完成度报表拿它当分母（一个班一学期多个计划时，按目标算比按某个计划算稳定），
--      将来自动排课也是按目标反推每周要铺几个格子；
--   2) 教师上限是档案属性，跨校区跨学期都跟着人走，写在计划里会变成每次排课都要重填。
--
-- 取值口径与 capacity_limit 不同，别改成一个：
--   capacity_limit 用 0 表示"不限"（历史如此，V73 的 CHECK 已按这个语义修过）；
--   这三列用 NULL 表示"未设定/不限"，因为 0 在这几列里是有意义的非法值（每周 0 节 = 不开课），
--   业务侧一律 `值 != null && 值 > 0` 才判超限。留空即取消限制，对应的实体字段标了
--   @TableField(updateStrategy = ALWAYS)，否则 updateById 会忽略 null、编辑时清空清不掉。
--
-- 教师上限超出只提示不拦（与停排日、学员跨班撞课同一档：口径由操作员掌握，系统不替他做主）。

ALTER TABLE `jp_class`
    ADD COLUMN `weekly_lessons` int NULL COMMENT '每周应上节数(自动排课目标与课时完成度分母,NULL=未设定)' AFTER `capacity_limit`;

ALTER TABLE `jp_teacher`
    ADD COLUMN `max_weekly_lessons` int NULL COMMENT '周课时上限(排课超出只提示不拦,NULL=不限)' AFTER `employment_type`,
    ADD COLUMN `max_daily_lessons` int NULL COMMENT '每日授课节数上限(排课超出只提示不拦,NULL=不限)' AFTER `max_weekly_lessons`;
