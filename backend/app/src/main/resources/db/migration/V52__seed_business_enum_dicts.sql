-- V52：把 5 个"用户可选、纯展示标签"的业务枚举统一收进系统字典
-- 背景：家长关系/报名渠道/用工类型/教师角色/校区类型此前各端写死选项、DTO 又声明成另一套大写值，
--   与 AGENTS「枚举值/字典规范」冲突。经排查后端代码不依赖这些具体值做分支（非状态机），可安全入字典。
-- 规范值沿用库里现行的小写值（lead/front_desk/mother/full_time/direct 等），不做大写改造。
-- 存量数据本机已是规范小写，唯一例外是 jp_class_teacher 列默认值遗留 'main'（已被 normalizeTeacherRole 映射为 lead），
--   本脚本把历史 'main' 行归一为 'lead'。

-- 1) 字典类型（接 V51 的 gender=id5，本批用 id 6~10）
INSERT IGNORE INTO sys_dict_type (
    id, type_code, type_name, status, remark, deleted
) VALUES
    (6, 'parent_relation', '家长关系', 'active', '学员监护人-与学员关系', 0),
    (7, 'enrollment_channel', '报名渠道', 'active', '报名来源渠道', 0),
    (8, 'employment_type', '用工类型', 'active', '教师用工类型', 0),
    (9, 'teacher_role', '教师角色', 'active', '班级教师任课角色', 0),
    (10, 'campus_type', '校区类型', 'active', '校区经营类型', 0);

-- 2) 字典条目（接 V51 的 item id42，本批用 id 43~59；item_code 与 item_value 同值）
INSERT IGNORE INTO sys_dict_item (
    id, type_id, item_code, item_name, item_value, sort_no, status, remark, deleted
) VALUES
    (43, 6, 'mother', '母亲', 'mother', 10, 'active', NULL, 0),
    (44, 6, 'father', '父亲', 'father', 20, 'active', NULL, 0),
    (45, 6, 'guardian', '监护人', 'guardian', 30, 'active', NULL, 0),
    (46, 6, 'relative', '其他亲属', 'relative', 40, 'active', NULL, 0),
    (47, 7, 'front_desk', '前台报名', 'front_desk', 10, 'active', NULL, 0),
    (48, 7, 'consultant', '顾问录入', 'consultant', 20, 'active', NULL, 0),
    (49, 7, 'online', '线上线索', 'online', 30, 'active', NULL, 0),
    (50, 7, 'referral', '转介绍', 'referral', 40, 'active', NULL, 0),
    (51, 8, 'full_time', '全职', 'full_time', 10, 'active', NULL, 0),
    (52, 8, 'part_time', '兼职', 'part_time', 20, 'active', NULL, 0),
    (53, 8, 'contract', '外聘', 'contract', 30, 'active', NULL, 0),
    (54, 9, 'lead', '主讲', 'lead', 10, 'active', NULL, 0),
    (55, 9, 'assistant', '助教', 'assistant', 20, 'active', NULL, 0),
    (56, 9, 'substitute', '代课', 'substitute', 30, 'active', NULL, 0),
    (57, 10, 'direct', '直营', 'direct', 10, 'active', NULL, 0),
    (58, 10, 'franchise', '加盟', 'franchise', 20, 'active', NULL, 0),
    (59, 10, 'partner', '合作', 'partner', 30, 'active', NULL, 0);

-- 3) 存量归一：教师角色历史遗留 'main' 统一为规范值 'lead'（列默认值曾为 main）
UPDATE jp_class_teacher SET teacher_role = 'lead' WHERE teacher_role = 'main';

-- 4) 列注释指向字典出处；MODIFY COLUMN 会替换整条列定义，类型/NULL/DEFAULT 必须原样照抄。
--    同时把 jp_class_teacher.teacher_role 的默认值由遗留的 'main' 改为规范值 'lead'。
ALTER TABLE jp_student_parent MODIFY COLUMN relation_type VARCHAR(32) NULL COMMENT '家长关系字典(parent_relation:mother=母亲,father=父亲,guardian=监护人,relative=其他亲属)';
ALTER TABLE jp_enrollment MODIFY COLUMN channel VARCHAR(32) NULL COMMENT '报名渠道字典(enrollment_channel:front_desk=前台报名,consultant=顾问录入,online=线上线索,referral=转介绍)';
ALTER TABLE jp_teacher MODIFY COLUMN employment_type VARCHAR(32) NOT NULL DEFAULT 'full_time' COMMENT '用工类型字典(employment_type:full_time=全职,part_time=兼职,contract=外聘)';
ALTER TABLE jp_class_teacher MODIFY COLUMN teacher_role VARCHAR(32) NOT NULL DEFAULT 'lead' COMMENT '教师角色字典(teacher_role:lead=主讲,assistant=助教,substitute=代课)';
ALTER TABLE jp_campus MODIFY COLUMN campus_type VARCHAR(32) NOT NULL DEFAULT 'direct' COMMENT '校区类型字典(campus_type:direct=直营,franchise=加盟,partner=合作)';
