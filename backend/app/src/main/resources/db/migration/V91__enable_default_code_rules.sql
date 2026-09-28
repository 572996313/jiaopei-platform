-- ============================================================================
-- V91：把 12 个主数据资源的编号规则默认启用
--
-- 为什么种这一批：V88 建 sys_code_rule 时刻意不种行（口径"不写行=未启用"），结果全站一条规则
--   都没开——新增页既不预填建议号、也不显示「重新生成」，编码照样得手填，现场直接反馈成
--   "编号功能没实现"。这一页的价值是"客户不用自己编"，默认全关等于把价值关掉了，所以按
--   代码登记的默认格式把 12 条全部种成启用。
--
-- 与底座 cornerstone/migration/V110__enable_institution_code_rule.sql 的关系（**故意不是一字不差**）：
--   V110 只种 institution 这一条——那是底座自己用 InstitutionCodeRuleContributor 登记的资源；
--   其余 11 条（校区/教室/学期/节次/教师/学员/班级/报名/课程/课程分类/班型）是本项目的行业
--   资源清单，写进 core 的迁移等于把教培的表塞给第二个消费方，底座边界不许（守护：
--   ExternalIdVocabularyGuardTest 扫 core 目录的 SQL）。institution 那两行在两份脚本里取值
--   与 id 完全一致，同一个新库无论走哪条路径都长出同样的行。
--
-- 写法：INSERT IGNORE + 唯一键 uk_sys_code_rule_resource(resource_code, deleted)。
--   已经在「编号规则」页改过格式或手动停用过的资源**不会被覆盖**，只补缺的——现场的人工设定
--   赢过默认值。要重置某条格式请直接在页面上改，不要在这里写 UPDATE。
--
-- 取值来源是代码登记的默认格式（前缀 / 分隔符 / 日期段 / 序号位数），改默认要两边一起改：
--   InstitutionCodeRuleContributor、OrganizationCodeRuleContributor、EducationCodeRuleContributor
--   与本文件。守护：CodeRuleContributionTest。
--
-- id 取 1001~1012 小号段：业务写入是雪花 19 位，这一段永远撞不上。
-- ============================================================================

INSERT IGNORE INTO sys_code_rule (
    id, resource_code, enabled, prefix, separator_char, date_pattern, serial_length, remark, deleted
) VALUES
    (1001, 'institution', 1, 'ORG', '-', 'yyyyMMdd', 3, '默认规则,可在编号规则页调整格式或停用', 0),
    (1002, 'campus', 1, 'CMP', '-', '', 3, '默认规则,可在编号规则页调整格式或停用', 0),
    (1003, 'classroom', 1, 'ROM', '-', '', 3, '默认规则,可在编号规则页调整格式或停用', 0),
    (1004, 'term', 1, 'TRM', '-', 'yyyy', 2, '默认规则,可在编号规则页调整格式或停用', 0),
    (1005, 'time_slot', 1, 'SLOT', '-', '', 3, '默认规则,可在编号规则页调整格式或停用', 0),
    (1006, 'teacher', 1, 'TCH', '-', '', 4, '默认规则,可在编号规则页调整格式或停用', 0),
    (1007, 'student', 1, 'STU', '-', 'yyyy', 4, '默认规则,可在编号规则页调整格式或停用', 0),
    (1008, 'class', 1, 'CLS', '-', 'yyyyMM', 3, '默认规则,可在编号规则页调整格式或停用', 0),
    (1009, 'enrollment', 1, 'ENR', '-', 'yyyyMM', 4, '默认规则,可在编号规则页调整格式或停用', 0),
    (1010, 'course', 1, 'CRS', '-', '', 3, '默认规则,可在编号规则页调整格式或停用', 0),
    (1011, 'course_category', 1, 'CAT', '-', '', 2, '默认规则,可在编号规则页调整格式或停用', 0),
    (1012, 'class_type', 1, 'TYP', '-', '', 2, '默认规则,可在编号规则页调整格式或停用', 0);
