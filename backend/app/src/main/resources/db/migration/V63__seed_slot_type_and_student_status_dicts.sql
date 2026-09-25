-- V63：时段类型与学员状态收进系统字典，并把三处枚举列注释补齐码值对照
-- 背景：AGENTS「枚举值/字典规范」要求"取值可枚举、给用户选择"的字段以字典为唯一事实源。
--   slot_type 与 student_status 全仓都没有任何按值的代码分支（只有空值回落默认值），
--   属于纯展示标签，此前却在前端两处各自写死中文 switch，且两份已经漂移
--   （break 一处译"课间休息"、一处译"休息"；student_status 未知值被兜底显示成"在读"）。
-- 存量核查：本机 jp_time_slot.slot_type / jp_student.status / jp_class_teacher.status 全是规范小写值，
--   所以本脚本只种字典、改注释，不 UPDATE 任何业务数据。
-- jp_class_teacher.status 不入字典：它是代码分支判定的启停状态
--   （ClassTeacherServiceImpl 与 ClassChangeNoticeServiceImpl 都按 status='active' 过滤任课关系），
--   按规范边界只补码值对照注释。

-- 1) 字典类型（接 V52 的 id6~10，本批用 id 11~12）
INSERT IGNORE INTO sys_dict_type (
    id, type_code, type_name, status, remark, deleted
) VALUES
    (11, 'slot_type', '时段类型', 'active', '时间段/时段模板-节次类型选项', 0),
    (12, 'student_status', '学员状态', 'active', '学员档案-在读状态选项', 0);

-- 2) 字典条目（接 V54 的 item id60，本批用 id 61~68；item_code 与 item_value 同值）
INSERT IGNORE INTO sys_dict_item (
    id, type_id, item_code, item_name, item_value, sort_no, status, remark, deleted
) VALUES
    (61, 11, 'morning_study', '早自习', 'morning_study', 10, 'active', NULL, 0),
    (62, 11, 'teaching', '授课', 'teaching', 20, 'active', NULL, 0),
    (63, 11, 'break', '课间休息', 'break', 30, 'active', NULL, 0),
    (64, 11, 'self_study', '晚自习', 'self_study', 40, 'active', NULL, 0),
    (65, 12, 'studying', '在读', 'studying', 10, 'active', NULL, 0),
    (66, 12, 'suspended', '停课', 'suspended', 20, 'active', NULL, 0),
    (67, 12, 'graduated', '已结业', 'graduated', 30, 'active', NULL, 0),
    (68, 12, 'withdrawn', '已退学', 'withdrawn', 40, 'active', NULL, 0);

-- 3) 列注释标注字典出处（列定义原样照抄，MODIFY 漏写会被隐式改列）
ALTER TABLE jp_time_slot MODIFY COLUMN `slot_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'teaching' COMMENT '时段类型字典(slot_type:morning_study=早自习,teaching=授课,break=课间休息,self_study=晚自习)';
ALTER TABLE jp_student MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'studying' COMMENT '学员状态字典(student_status:studying=在读,suspended=停课,graduated=已结业,withdrawn=已退学)';
ALTER TABLE jp_class_teacher MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,inactive=停用;停用后不计入班级任课教师)';
