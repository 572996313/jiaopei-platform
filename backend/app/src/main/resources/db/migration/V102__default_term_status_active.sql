-- V102: 学期状态的建库默认值由 draft（草稿）改为 active（启用），并把取值写进列注释。
--
-- 起因：新建学期实际就是「马上要排课、建收费台账的」，默认草稿没有任何业务含义
-- （全仓库没有任何逻辑读 jp_term.status：FeeLedgerGuard / TeachingClassServiceImpl /
-- ScheduleAutoBatchServiceImpl 都只校验存在性、机构归属与起止日期；排课那道 active 闸门
-- 卡的是校区状态，不是学期）。默认值停在 draft 只会让新学期的标签看着像半成品。
-- 应用层两处默认值（前端表单初值/重置/回显兜底、后端 createTerm 兜底）已同步改成 active，
-- 本脚本只把「不走应用层、直接写库」时的默认值对齐，避免两条路径长出两种状态。
--
-- 不改已有数据：存量 status='draft' 的学期保持原状（那是现场真实状态，不是默认值问题）。
-- 兼容自查：单条 ALTER TABLE，无 CTE/窗口函数/JSON_TABLE/IF( 函数、无 MariaDB 方言；
-- 不建表、不涉及排序规则漂移；MODIFY COLUMN 的类型与 NULL 声明与 V13 后的实际定义一字不差，
-- 仅改 DEFAULT 与 COMMENT，可整脚本重放。

ALTER TABLE `jp_term`
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态 draft草稿,active启用,archived归档';
