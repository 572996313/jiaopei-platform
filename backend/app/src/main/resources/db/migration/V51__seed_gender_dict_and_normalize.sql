-- V51: 性别统一走系统字典 gender
-- 背景：PC 学员表单写死小写 male/female，小程序写死大写 MALE/FEMALE/UNKNOWN，
-- 同一列 jp_student.gender 里混出了两套值。本脚本种入字典、归一存量、并把列注释指向字典。
-- 归一后两端下拉/回显一律读字典（/api/system/dicts/options?typeCodes=gender），后端写入按字典校验。

INSERT IGNORE INTO sys_dict_type (
    id, type_code, type_name, status, remark, deleted
) VALUES
    (5, 'gender', '性别', 'active', '学员/教师-性别选项(PC与小程序共用)', 0);

INSERT IGNORE INTO sys_dict_item (
    id, type_id, item_code, item_name, item_value, sort_no, status, remark, deleted
) VALUES
    (40, 5, 'male', '男', 'male', 10, 'active', NULL, 0),
    (41, 5, 'female', '女', 'female', 20, 'active', NULL, 0),
    (42, 5, 'unknown', '未知', 'unknown', 30, 'active', NULL, 0);

-- 存量归一为小写字典值。排序规则 utf8mb4_unicode_ci 下 WHERE 不区分大小写，
-- 命中的即历史大写/混写行，SET 后统一为字典 item_value。
UPDATE jp_student SET gender = 'male' WHERE gender = 'MALE';
UPDATE jp_student SET gender = 'female' WHERE gender = 'FEMALE';
UPDATE jp_student SET gender = 'unknown' WHERE gender = 'UNKNOWN';
UPDATE jp_student SET gender = NULL WHERE gender = '';
UPDATE jp_teacher SET gender = 'male' WHERE gender = 'MALE';
UPDATE jp_teacher SET gender = 'female' WHERE gender = 'FEMALE';
UPDATE jp_teacher SET gender = 'unknown' WHERE gender = 'UNKNOWN';
UPDATE jp_teacher SET gender = NULL WHERE gender = '';

-- 列注释标注字典出处（列定义原样照抄 varchar(16) NULL，MODIFY 漏写会被隐式改列）
ALTER TABLE jp_student MODIFY COLUMN gender VARCHAR(16) NULL COMMENT '性别字典(gender:male=男,female=女,unknown=未知)';
ALTER TABLE jp_teacher MODIFY COLUMN gender VARCHAR(16) NULL COMMENT '性别字典(gender:male=男,female=女,unknown=未知)';
