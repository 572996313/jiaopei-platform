-- ============================================================================
-- V110：给「机构编码」种下默认启用的编号规则
--
-- 与教培 app/.../db/migration/V91__enable_default_code_rules.sql 是同一件事的两条路径，
-- 但**两份内容故意不相同**，这是对 AGENTS「双轨迁移一字不差」的一条有意偏离，理由是底座边界：
--   sys_code_rule 的 V88/本仓库 V108 只建表、不种行（口径就是"不写行=未启用"），而这一页要
--   开箱可用就得有人种行。资源清单是代码登记的（各模块 CodeRuleContributor），底座只认识
--   institution 这一条，其余（校区/班级/学员/课程…）是本项目的行业词汇，写进 core 的迁移
--   等于把教培的资源表塞给第二个消费方（守护：ExternalIdVocabularyGuardTest 会扫本目录的 SQL）。
--   所以：**core 只种 institution；下游要默认启用别的资源，在自己项目的迁移里补。**
--
-- 为什么现在要种：规则默认全关时，新增页既不预填也不显示「重新生成」，客户看不出这个功能存在
--   （现场反馈"好多功能没实现"，根因就是 sys_code_rule 一条启用行都没有）。
--
-- 写法：INSERT IGNORE + 唯一键 uk_sys_code_rule_resource(resource_code, deleted)。
--   操作员已经在页面上改过格式或手动停用过的资源，这里一律**不覆盖**，只补缺的那几条——
--   默认值不该赢过现场已经生效的人工设定。
--
-- id 取 1001 起的小号段：业务写入是雪花（19 位），这一段永远撞不上；且两条路径用同一个 id，
--   同一个新库无论走哪条都长出一样的行。
-- ============================================================================

INSERT IGNORE INTO sys_code_rule (
    id, resource_code, enabled, prefix, separator_char, date_pattern, serial_length, remark, deleted
) VALUES
    (1001, 'institution', 1, 'ORG', '-', 'yyyyMMdd', 3, '默认规则,可在编号规则页调整格式或停用', 0);
