-- V64：教师状态收进系统字典
-- 背景：AGENTS「枚举值/字典规范」要求"取值可枚举、给用户选择"的字段以字典为唯一事实源。
--   排查结论：全仓没有任何代码分支依赖 jp_teacher.status 的具体值——它只出现在两处，
--   一是教师列表的筛选条件（TeacherServiceImpl#buildWrapper 按传入值 eq），二是批量导入的取值白名单。
--   登录态判的是 jp_user_account.status，任课关系判的是 jp_class_teacher.status，都与教师档案状态无关，
--   所以教师状态属"纯展示标签"，可以入字典让运维扩值。
-- 存量核查：本机 jp_teacher.status 只有 active，无需归一，本脚本只种字典、改列注释。

-- 1) 字典类型（接 V63 的 id11~12，本批用 id 13）
INSERT IGNORE INTO sys_dict_type (
    id, type_code, type_name, status, remark, deleted
) VALUES
    (13, 'teacher_status', '教师状态', 'active', '教师档案-在职状态选项', 0);

-- 2) 字典条目（接 V63 的 item id68，本批用 id 69~71；item_code 与 item_value 同值）
INSERT IGNORE INTO sys_dict_item (
    id, type_id, item_code, item_name, item_value, sort_no, status, remark, deleted
) VALUES
    (69, 13, 'active', '在职', 'active', 10, 'active', NULL, 0),
    (70, 13, 'suspended', '暂停', 'suspended', 20, 'active', NULL, 0),
    (71, 13, 'left', '离职', 'left', 30, 'active', NULL, 0);

-- 3) 列注释指向字典出处；MODIFY COLUMN 会替换整条列定义，类型/NULL/DEFAULT 必须原样照抄。
ALTER TABLE jp_teacher MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '教师状态字典(teacher_status:active=在职,suspended=暂停,left=离职)';
