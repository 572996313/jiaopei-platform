-- 课时目标的录入口径与整班总课时：让操作员按自己手上真有的数来填。
--
-- 为什么加这三列，而不是把 weekly_lessons 换成「目标口径 + 目标值」两列三选一：
--   1) 周目标与总课时是两个维度，不是同一件事的两种说法。「合同一共 40 节、每周上 2 节」是最常见的
--      真实形态，做成单选一栏会把这个组合直接排除掉；
--   2) weekly_lessons 是完成度报表与一键补排的唯一分母（全仓库只有 ScheduleCompletenessServiceImpl#buildRow
--      读它），保持这一列的含义与读法不变，报表和引擎就一个字都不用改。
--
-- weekly_lessons_mode 只修饰 weekly_lessons，不描述总课时，列名把这个从属关系写死。
-- 它存在的唯一理由是编辑弹窗要能还原「我当时填的是每月 8 节」，而不是让用户面对一个自己没填过的 2。
-- 换算在保存时做一次并落 weekly_lessons，原值留在 weekly_lessons_input（见 LessonTargetConverter）。
--
-- 每月→每周按 round(每月 × 12 ÷ 52) 取整，52 用整数常量：算式要能被操作员拿计算器复验，
-- 写成 ÷4.348 就没人信这个数是怎么来的。
-- 低频班（每月 1~4 节）换算结果不足 1，此时 weekly_lessons 留 NULL、原值照留，界面上说明原因。
-- 注意**写 0 与写 NULL 在读取侧完全等价**（判定是 `!= null && > 0`，0 会静默回落到计划展开，
-- 不会像 V75 头部字面读起来那样「被当成这周不该排课」）；之所以仍要写 NULL，是因为 0 在本项目是
-- 非法值，存一个与「没存」等价、却会误导下一个读代码的人的值没有任何好处。
--
-- total_lessons 只用于课时进度，不参与任何分母、缺口与补排计算。
-- 三列都可空，实体上一律标 @TableField(updateStrategy = ALWAYS)，否则编辑时清空无效。
--
-- 唯一的 varchar 列显式写了 COLLATE（防排序规则随服务器默认漂移），注意**列定义里写成
-- `COLLATE utf8mb4_unicode_ci`，带等号的 `COLLATE=` 只有表选项那一层合法**，写在列上直接 1064。

ALTER TABLE `jp_class`
    ADD COLUMN `weekly_lessons_mode`  varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'weekly'
        COMMENT '每周应上节数的录入口径(weekly=按周填,monthly=按月填由系统换算)' AFTER `weekly_lessons`,
    ADD COLUMN `weekly_lessons_input` int NULL
        COMMENT '每周应上节数的录制原值(mode=monthly 时存的是每月节数,换算结果在 weekly_lessons;NULL=未设定)' AFTER `weekly_lessons_mode`,
    ADD COLUMN `total_lessons`        int NULL
        COMMENT '整班总课时(合同/教学计划的总节数,NULL=未设定;只用于课时进度,不进完成度分母)' AFTER `weekly_lessons_input`;

-- 回填：存量 weekly_lessons 全部是「按周填」的，原值就是它自身
UPDATE `jp_class`
SET `weekly_lessons_input` = `weekly_lessons`
WHERE `weekly_lessons` IS NOT NULL;
