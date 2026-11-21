-- V110: 收费域小数位加宽，金额 2 位不够用于按天/半月分摊。
--
-- 起因：日标准 × 出勤天数、月标准 × 半月系数、以及收款冲正都可能产生多位小数。
-- 原 DECIMAL(x,2) 在入库时直接四舍五入，财务看到的合计与逐月明细会差几分钱。
--
-- 新口径（只扩大精度，不改变任何现有值）：
--   金额（应收/实收/发生/结余/收款/上期结余/其他抵扣） DECIMAL(16,4)
--   单价（价目/月标准/日标准）                          DECIMAL(16,6)
--   月数/出勤天数/月份系数                              DECIMAL(8,4)
--
-- 兼容自查：
-- 1) 只写普通 ALTER TABLE ... MODIFY COLUMN，无 CTE/窗口函数/MariaDB 方言；MySQL 8.x 原样执行。
-- 2) 扩大 DECIMAL 精度不会截断现有数据，原值补零保留；列定义中的 NULL/DEFAULT/COMMENT 照抄。
-- 3) 不新增/删除索引，不动外键；MODIFY COLUMN 保留列上的约束。
-- 4) 半实施时按表逐条重跑即可（每个 ALTER 只改一张表，可重复执行相同结果）。

ALTER TABLE `jp_fee_price`
  MODIFY COLUMN `monthly_price` decimal(16,6) DEFAULT NULL COMMENT '月标准(应收=收费月数×月标准,NULL=该维度不提供)',
  MODIFY COLUMN `daily_price` decimal(16,6) DEFAULT NULL COMMENT '日标准(按天项目当月发生=出勤天数×日标准,NULL=该维度不提供)';

ALTER TABLE `jp_fee_ledger_item`
  MODIFY COLUMN `billable_months` decimal(8,4) NOT NULL DEFAULT '0.0000' COMMENT '收费月数(可为小数,如 5.5)',
  MODIFY COLUMN `monthly_standard` decimal(16,6) NOT NULL DEFAULT '0.000000' COMMENT '收费标准-月标准快照',
  MODIFY COLUMN `daily_rate` decimal(16,6) NOT NULL DEFAULT '0.000000' COMMENT '日标准快照(按天项目用)',
  MODIFY COLUMN `opening_balance` decimal(16,4) NOT NULL DEFAULT '0.0000' COMMENT '上期结余(正=预收,负=欠费)',
  MODIFY COLUMN `other_deduction` decimal(16,4) NOT NULL DEFAULT '0.0000' COMMENT '其他抵扣';

ALTER TABLE `jp_fee_payment`
  MODIFY COLUMN `amount` decimal(16,4) NOT NULL COMMENT '收款金额(正数;负数=冲正/退回)';

ALTER TABLE `jp_fee_monthly`
  MODIFY COLUMN `month_factor` decimal(8,4) NOT NULL DEFAULT '1.0000' COMMENT '月数系数(1.0000=整月,0.5000=半月;按天项目不使用)',
  MODIFY COLUMN `auto_amount` decimal(16,4) NOT NULL DEFAULT '0.0000' COMMENT '引擎自动金额(保留对账与回显)',
  MODIFY COLUMN `occurrence_amount` decimal(16,4) NOT NULL DEFAULT '0.0000' COMMENT '最终发生额(覆盖后)';

ALTER TABLE `jp_fee_attendance_month`
  MODIFY COLUMN `attendance_days` decimal(8,4) NOT NULL DEFAULT '0.0000' COMMENT '生效出勤天数(可手工改)',
  MODIFY COLUMN `auto_days` decimal(8,4) NOT NULL DEFAULT '0.0000' COMMENT '考勤汇总自动值(保留对照)';