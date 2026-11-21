-- V103: 学期编码/名称的唯一约束改为「只约束未删除行」，软删墓碑不再占住业务键。
--
-- 起因：jp_term 的两把唯一键 uk_jp_term_code / uk_jp_term_name 都不含 deleted。删除学期走
-- @TableLogic 逻辑删除，墓碑行仍占着编码与名称：现场删掉「上半年」后同名重建，insert 直接
-- 撞 1062（uk_jp_term_name）；而应用层预检走 MyBatis-Plus，会自动加 deleted = 0，查不到墓碑，
-- 现象就是「库里明明删了，还提示已存在」。
--
-- 形态照 V72 的生成列守卫：未删除行取「机构ID#编码 / 机构ID#名称」，墓碑取 NULL，唯一键建在
-- 守卫列上。NULL 不参与唯一比对，所以可同时存在多条墓碑，「删除→重建同键→再删」也不会像
-- (业务键, deleted) 那样第二次撞键。
--
-- 不走物理删除：jp_class / jp_fee_ledger / jp_schedule_plan / jp_schedule_auto_batch /
-- schedule_lesson 都有指向 jp_term 的外键，软删子行同样占着外键（1451）。
--
-- 兼容自查：
-- 1) 普通 DDL，无 CTE/窗口函数/JSON_TABLE/IF( 函数；条件用 CASE WHEN。
-- 2) 生成列不写可空声明（MySQL 1064），COLLATE 显式写在类型后；被拼进的列均 NOT NULL。
-- 3) 先建新键（含 fk_jp_term_institution 需要的最左前缀索引 idx_jp_term_institution）再删旧键，
--    避免 1553；新旧键不同名，不会 1061。
-- 4) 本地 MySQL 9.7.2 已实测；生产 8.4 待验（生成列 8.0 起支持，写法与 V72 同源）。

ALTER TABLE `jp_term`
    ADD COLUMN `term_code_guard` VARCHAR(96) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `institution_id`, `term_code`) END
        ) STORED COMMENT '学期编码防重键(未删除时取 机构ID#学期编码,软删墓碑为 NULL)',
    ADD COLUMN `term_name_guard` VARCHAR(160) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `institution_id`, `term_name`) END
        ) STORED COMMENT '学期名称防重键(未删除时取 机构ID#学期名称,软删墓碑为 NULL)',
    ADD KEY `idx_jp_term_institution` (`institution_id`),
    ADD UNIQUE KEY `uk_jp_term_code_guard` (`term_code_guard`),
    ADD UNIQUE KEY `uk_jp_term_name_guard` (`term_name_guard`);

ALTER TABLE `jp_term`
    DROP INDEX `uk_jp_term_code`,
    DROP INDEX `uk_jp_term_name`;
