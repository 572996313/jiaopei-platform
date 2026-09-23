-- V50: 恢复被 V31 抹掉的 deleted 列默认值（微信注册/学员绑定插入报
-- "Field 'deleted' doesn't have a default value" 的根因修复）
-- V31 补字段注释时对 deleted 用了 `MODIFY COLUMN ... NOT NULL COMMENT ...`，
-- 未照抄 V26 原定义里的 `DEFAULT 0`，MySQL 的 MODIFY 是整列定义替换，默认值被静默移除。
-- 实体继承 BaseEntity 时 deleted 初始为 null，MyBatis-Plus 插入省略 null 字段，
-- 于是只能靠库侧默认值兜底。本脚本把两张表的定义归一，其余表经排查未受影响。
ALTER TABLE `jp_wechat_binding`
    MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)';

ALTER TABLE `jp_user_student_rel`
    MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)';
