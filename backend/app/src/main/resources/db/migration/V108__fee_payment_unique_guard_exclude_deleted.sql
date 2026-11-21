-- V108: 收款单号的唯一约束改为「只约束未删除行」，软删墓碑不再占住单号。
--
-- 起因：uk_jp_fee_payment_no (institution_id, payment_no, deleted) 里 deleted 只有 0/1，
-- 同一个单号只容得下一条墓碑。现场 2026-11-21 的操作序列是：登记 SK-20261121-0002 → 删除
-- （留下墓碑）→ 再登记（取号按「未删除流水数 + 1」，把已删的 -0002 又发了一次）→ 再删除时
-- UPDATE deleted = 1 撞上第一条墓碑，报 1062 Duplicate entry '1-SK-20261121-0002-1'。
--
-- 收款是财务凭证，墓碑要留痕，所以这里不按「B 类改物理删」处理（见 AGENTS「逻辑删除唯一键
-- 收口」），而是换成守卫列：未删除行取「机构ID#收款单号」，墓碑取 NULL。NULL 不参与唯一比对，
-- 同一单号可以同时存在任意多条墓碑，「删 → 重建同号 → 再删」也不会第二次撞键。
-- 取号侧同步改为「含墓碑取当天最大序号 + 1」（FeePaymentMapper#maxPaymentNoSequence）：
-- 删掉的号不再回收，历史与导出里不会出现两张同号单据。
--
-- 兼容自查：
-- 1) 普通 DDL，无 CTE/窗口函数/JSON_TABLE/IF( 函数；条件用 CASE WHEN。
-- 2) 生成列不写可空声明（MySQL 1064），COLLATE 显式写在类型后；被拼进的列均 NOT NULL。
-- 3) 先建新键再删旧键；fk_jp_fee_payment_institution 的最左前缀由
--    idx_jp_fee_payment_scope (institution_id, campus_id, paid_on) 继续兜住，不会 1553；
--    新增的 idx_jp_fee_payment_no 同时服务取号查询。新旧键不同名，不会 1061。
-- 4) jp_fee_payment 不被任何表引用（information_schema 已核），没有子表外键顾虑；
--    它自己的四条外键只依赖本表索引，DROP 旧键不影响。
-- 5) 本地 MySQL 9.7.2 已实测；生产 8.4 待验（生成列 8.0 起支持，写法与 V72/V103 同源）。

ALTER TABLE `jp_fee_payment`
    ADD COLUMN `payment_no_guard` VARCHAR(96) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `institution_id`, `payment_no`) END
        ) STORED COMMENT '收款单号防重键(未删除时取 机构ID#收款单号,软删墓碑为 NULL)',
    ADD KEY `idx_jp_fee_payment_no` (`institution_id`, `payment_no`),
    ADD UNIQUE KEY `uk_jp_fee_payment_no_guard` (`payment_no_guard`);

ALTER TABLE `jp_fee_payment`
    DROP INDEX `uk_jp_fee_payment_no`;
