-- 排课规则的约束对象从「班级/教师」扩到「教室」：自动排课要能表达"3 号音乐教室周一整天空着要排练"
-- 这类限制，只按班级和教师拦不住同一间教室被两个班占用。
--
-- 没有 DDL 结构变更：target_id 复用同一列，按 target_type 解释（class=班级ID、teacher=教师ID、
-- classroom=教室ID），与 V58 原本的设计一致，因此只改注释把新的取值写清楚——
-- MODIFY COLUMN 的类型与 NULL 声明必须与 V13 后的实际定义一字不差，否则会隐式改列。
-- 校验落在应用层（ScheduleRuleServiceImpl 按 target_type 分别核对对象属于本机构/本校区），
-- 数据库不建外键：一列存三种对象的 id，加不了可判别的外键。

ALTER TABLE `jp_schedule_rule`
    MODIFY COLUMN `target_type` varchar(16) NOT NULL COMMENT '规则对象类型(class=班级,teacher=教师,classroom=教室)',
    MODIFY COLUMN `target_id` bigint NOT NULL COMMENT '规则对象ID(target_type=class时为班级ID,=teacher时为教师ID,=classroom时为教室ID)';

ALTER TABLE `jp_schedule_rule` COMMENT = '排课规则(班级/教师/教室的星期×节次禁排必排约束)';
