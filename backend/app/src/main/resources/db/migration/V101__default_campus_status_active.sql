-- V101: 校区状态的建库默认值由 planning（规划中）改为 active（启用中），并把取值写进列注释。
--
-- 起因：新建校区实际是「马上要用的」，默认规划中会让新校区在改成启用中之前
-- 无法执行自动排课（ScheduleAutoBatchServiceImpl 只放行 active）。
-- 应用层三处默认值（前端表单初值 / 重置 / 后端 createCampus 兜底）已同步改成 active，
-- 本脚本只把「不走应用层、直接写库」时的默认值对齐，避免两条路径长出两种状态。
--
-- 不改已有数据：存量 status='planning' 的校区保持原状（那是真实业务状态，不是默认值问题）。
-- 兼容自查：单条 ALTER TABLE，无 CTE/窗口函数/JSON_TABLE/IF( 函数、无 MariaDB 方言；
-- 不建表、不涉及排序规则漂移；MODIFY COLUMN 的类型与 NULL 声明与 V13 后的实际定义一字不差，
-- 仅改 DEFAULT 与 COMMENT，可整脚本重放。

ALTER TABLE `jp_campus`
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态 planning规划中,active启用中,suspended暂停中,closed已关闭';
