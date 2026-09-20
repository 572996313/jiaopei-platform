-- 组织架构
ALTER TABLE `jp_institution` COMMENT = '培训机构';
ALTER TABLE `jp_campus` COMMENT = '校区';
ALTER TABLE `jp_classroom` COMMENT = '教室';

-- 人员
ALTER TABLE `jp_student` COMMENT = '学员';
ALTER TABLE `jp_student_parent` COMMENT = '学员家长';
ALTER TABLE `jp_teacher` COMMENT = '教师';

-- 课程与班级
ALTER TABLE `jp_course_category` COMMENT = '课程分类';
ALTER TABLE `jp_course` COMMENT = '课程';
ALTER TABLE `jp_class_type` COMMENT = '班型';
ALTER TABLE `jp_term` COMMENT = '学期';
ALTER TABLE `jp_class` COMMENT = '班级';
ALTER TABLE `jp_class_student` COMMENT = '班级学员';
ALTER TABLE `jp_class_teacher` COMMENT = '班级教师';
ALTER TABLE `jp_class_opening_rule` COMMENT = '开班规则';
ALTER TABLE `jp_class_change_notice` COMMENT = '班级变更通知';
ALTER TABLE `jp_enrollment` COMMENT = '报名记录';
ALTER TABLE `jp_attendance` COMMENT = '考勤';

-- 排课
ALTER TABLE `jp_time_slot` COMMENT = '时段模板';
ALTER TABLE `schedule_lesson` COMMENT = '课次排课';
ALTER TABLE `schedule_teacher_rel` COMMENT = '课次授课老师';

-- 账号权限
ALTER TABLE `jp_user_account` COMMENT = '用户账号';
ALTER TABLE `jp_role` COMMENT = '角色';
ALTER TABLE `jp_user_role_rel` COMMENT = '用户角色关系';
ALTER TABLE `jp_user_token` COMMENT = '登录令牌';
ALTER TABLE `sys_menu` COMMENT = '系统菜单';
ALTER TABLE `sys_role_menu` COMMENT = '角色菜单关系';

-- 系统基础
ALTER TABLE `sys_dict_type` COMMENT = '字典类型';
ALTER TABLE `sys_dict_item` COMMENT = '字典项';

-- 可选：框架表，建议不改
 ALTER TABLE `flyway_schema_history` COMMENT = '数据库迁移历史';


-- =========================================================
-- 1. 组织架构
-- =========================================================

ALTER TABLE `jp_institution`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `org_code` varchar(64) NOT NULL COMMENT '机构编码',
    MODIFY COLUMN `org_name` varchar(128) NOT NULL COMMENT '机构名称',
    MODIFY COLUMN `short_name` varchar(64) NULL COMMENT '机构简称',
    MODIFY COLUMN `contact_name` varchar(64) NULL COMMENT '联系人',
    MODIFY COLUMN `contact_phone` varchar(32) NULL COMMENT '联系电话',
    MODIFY COLUMN `province_code` varchar(32) NULL COMMENT '省份编码',
    MODIFY COLUMN `city_code` varchar(32) NULL COMMENT '城市编码',
    MODIFY COLUMN `district_code` varchar(32) NULL COMMENT '区县编码',
    MODIFY COLUMN `address` varchar(255) NULL COMMENT '详细地址',
    MODIFY COLUMN `timezone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '时区',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'draft' COMMENT '状态 draft草稿',
    MODIFY COLUMN `opened_at` date NULL COMMENT '开业日期',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_campus`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `campus_code` varchar(64) NOT NULL COMMENT '校区编码',
    MODIFY COLUMN `campus_name` varchar(128) NOT NULL COMMENT '校区名称',
    MODIFY COLUMN `campus_type` varchar(32) NOT NULL DEFAULT 'direct' COMMENT '校区类型 direct直营',
    MODIFY COLUMN `phone` varchar(32) NULL COMMENT '联系电话',
    MODIFY COLUMN `principal_name` varchar(64) NULL COMMENT '校长姓名',
    MODIFY COLUMN `province_code` varchar(32) NULL COMMENT '省份编码',
    MODIFY COLUMN `city_code` varchar(32) NULL COMMENT '城市编码',
    MODIFY COLUMN `district_code` varchar(32) NULL COMMENT '区县编码',
    MODIFY COLUMN `address` varchar(255) NOT NULL COMMENT '详细地址',
    MODIFY COLUMN `capacity_limit` int NULL COMMENT '容量上限',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'planning' COMMENT '状态 planning规划中',
    MODIFY COLUMN `opened_at` date NULL COMMENT '开业日期',
    MODIFY COLUMN `sort_no` int NULL COMMENT '排序号',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_classroom`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `campus_id` bigint NOT NULL COMMENT '校区ID',
    MODIFY COLUMN `classroom_code` varchar(64) NOT NULL COMMENT '教室编码',
    MODIFY COLUMN `classroom_name` varchar(128) NOT NULL COMMENT '教室名称',
    MODIFY COLUMN `room_type` varchar(32) NOT NULL DEFAULT 'normal' COMMENT '教室类型 normal普通',
    MODIFY COLUMN `floor_no` varchar(32) NULL COMMENT '楼层',
    MODIFY COLUMN `seat_capacity` int NOT NULL DEFAULT 0 COMMENT '座位数',
    MODIFY COLUMN `area_sqm` decimal(10,2) NULL COMMENT '面积平方米',
    MODIFY COLUMN `equipment_tags` json NULL COMMENT '设备标签JSON',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'idle' COMMENT '状态 idle空闲',
    MODIFY COLUMN `allow_scheduling` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否允许排课 1是 0否',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

-- =========================================================
-- 2. 人员
-- =========================================================

ALTER TABLE `jp_student`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `campus_id` bigint NULL COMMENT '所属校区ID',
    MODIFY COLUMN `student_no` varchar(64) NOT NULL COMMENT '学号',
    MODIFY COLUMN `student_name` varchar(128) NOT NULL COMMENT '学员姓名',
    MODIFY COLUMN `gender` varchar(16) NULL COMMENT '性别',
    MODIFY COLUMN `birthday` date NULL COMMENT '出生日期',
    MODIFY COLUMN `guardian_name` varchar(128) NULL COMMENT '监护人姓名',
    MODIFY COLUMN `guardian_phone` varchar(32) NULL COMMENT '监护人电话',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'studying' COMMENT '状态 studying在读',
    MODIFY COLUMN `enrolled_at` date NULL COMMENT '入学日期',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_student_parent`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `student_id` bigint NOT NULL COMMENT '学员ID',
    MODIFY COLUMN `parent_name` varchar(128) NOT NULL COMMENT '家长姓名',
    MODIFY COLUMN `relation_type` varchar(32) NULL COMMENT '与学员关系',
    MODIFY COLUMN `parent_phone` varchar(32) NULL COMMENT '家长手机',
    MODIFY COLUMN `wechat_no` varchar(64) NULL COMMENT '微信号',
    MODIFY COLUMN `is_primary` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否主联系人 1是 0否',
    MODIFY COLUMN `is_emergency_contact` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否紧急联系人 1是 0否',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态 active有效',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_teacher`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `campus_id` bigint NULL COMMENT '所属校区ID',
    MODIFY COLUMN `teacher_code` varchar(64) NOT NULL COMMENT '教师工号',
    MODIFY COLUMN `teacher_name` varchar(128) NOT NULL COMMENT '教师姓名',
    MODIFY COLUMN `phone` varchar(32) NULL COMMENT '联系电话',
    MODIFY COLUMN `gender` varchar(16) NULL COMMENT '性别',
    MODIFY COLUMN `title` varchar(64) NULL COMMENT '职称',
    MODIFY COLUMN `employment_type` varchar(32) NOT NULL DEFAULT 'full_time' COMMENT '用工类型 full_time全职',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态 active在职',
    MODIFY COLUMN `hired_at` date NULL COMMENT '入职日期',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

-- =========================================================
-- 3. 课程与班级
-- =========================================================

ALTER TABLE `jp_course_category`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `category_code` varchar(64) NOT NULL COMMENT '分类编码',
    MODIFY COLUMN `category_name` varchar(128) NOT NULL COMMENT '分类名称',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态 active启用',
    MODIFY COLUMN `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序号',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_course`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `category_id` bigint NULL COMMENT '课程分类ID',
    MODIFY COLUMN `course_code` varchar(64) NOT NULL COMMENT '课程编码',
    MODIFY COLUMN `course_name` varchar(128) NOT NULL COMMENT '课程名称',
    MODIFY COLUMN `subject_name` varchar(64) NULL COMMENT '科目名称',
    MODIFY COLUMN `teaching_method` varchar(32) NULL COMMENT '授课方式',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态 active启用',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_class_type`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `type_code` varchar(64) NOT NULL COMMENT '班型编码',
    MODIFY COLUMN `type_name` varchar(128) NOT NULL COMMENT '班型名称',
    MODIFY COLUMN `teaching_method` varchar(32) NULL COMMENT '授课方式',
    MODIFY COLUMN `min_students` int NOT NULL DEFAULT 0 COMMENT '最少人数',
    MODIFY COLUMN `max_students` int NULL COMMENT '最多人数',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态 active启用',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_term`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `term_code` varchar(64) NOT NULL COMMENT '学期编码',
    MODIFY COLUMN `term_name` varchar(128) NOT NULL COMMENT '学期名称',
    MODIFY COLUMN `academic_year` varchar(16) NOT NULL COMMENT '学年',
    MODIFY COLUMN `season` varchar(32) NULL COMMENT '学季 春/秋等',
    MODIFY COLUMN `start_date` date NOT NULL COMMENT '学期开始日期',
    MODIFY COLUMN `end_date` date NOT NULL COMMENT '学期结束日期',
    MODIFY COLUMN `enrollment_start_date` date NULL COMMENT '报名开始日期',
    MODIFY COLUMN `enrollment_end_date` date NULL COMMENT '报名结束日期',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'draft' COMMENT '状态 draft草稿',
    MODIFY COLUMN `is_default` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否默认学期 1是 0否',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_class`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `campus_id` bigint NOT NULL COMMENT '校区ID',
    MODIFY COLUMN `term_id` bigint NULL COMMENT '学期ID',
    MODIFY COLUMN `course_id` bigint NULL COMMENT '课程ID',
    MODIFY COLUMN `class_type_id` bigint NULL COMMENT '班型ID',
    MODIFY COLUMN `class_code` varchar(64) NOT NULL COMMENT '班级编码',
    MODIFY COLUMN `class_name` varchar(128) NOT NULL COMMENT '班级名称',
    MODIFY COLUMN `teacher_id` bigint NULL COMMENT '班主任/主讲教师ID',
    MODIFY COLUMN `capacity_limit` int NOT NULL DEFAULT 0 COMMENT '班容上限',
    MODIFY COLUMN `current_students` int NOT NULL DEFAULT 0 COMMENT '当前学员数',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'planning' COMMENT '状态 planning规划中',
    MODIFY COLUMN `opened_at` date NULL COMMENT '开班日期',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_class_student`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `class_id` bigint NOT NULL COMMENT '班级ID',
    MODIFY COLUMN `student_id` bigint NOT NULL COMMENT '学员ID',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '在班状态 active在读',
    MODIFY COLUMN `joined_at` date NOT NULL COMMENT '入班日期',
    MODIFY COLUMN `left_at` date NULL COMMENT '退班日期',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_class_teacher`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `class_id` bigint NOT NULL COMMENT '班级ID',
    MODIFY COLUMN `teacher_id` bigint NOT NULL COMMENT '教师ID',
    MODIFY COLUMN `teacher_role` varchar(32) NOT NULL DEFAULT 'main' COMMENT '教师角色 main主讲',
    MODIFY COLUMN `is_primary` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否班主任 1是 0否',
    MODIFY COLUMN `joined_at` date NULL COMMENT '任教开始日期',
    MODIFY COLUMN `left_at` date NULL COMMENT '任教结束日期',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态 active在任',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_class_opening_rule`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `campus_id` bigint NULL COMMENT '校区ID 空表示机构级',
    MODIFY COLUMN `course_id` bigint NULL COMMENT '课程ID 空表示不限课程',
    MODIFY COLUMN `class_type_id` bigint NULL COMMENT '班型ID 空表示不限班型',
    MODIFY COLUMN `teacher_id` bigint NULL COMMENT '教师ID 空表示不限教师',
    MODIFY COLUMN `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
    MODIFY COLUMN `min_students` int NOT NULL DEFAULT 0 COMMENT '最少开班人数',
    MODIFY COLUMN `max_students` int NULL COMMENT '最多人数',
    MODIFY COLUMN `allow_pre_open` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否允许预开班 1是 0否',
    MODIFY COLUMN `impact_scope` varchar(32) NOT NULL DEFAULT 'schedule_and_execution' COMMENT '影响范围 schedule_and_execution排课及执行',
    MODIFY COLUMN `priority_no` int NOT NULL DEFAULT 0 COMMENT '优先级 数字越大越优先',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态 active启用',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_class_change_notice`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `campus_id` bigint NULL COMMENT '校区ID',
    MODIFY COLUMN `class_id` bigint NOT NULL COMMENT '班级ID',
    MODIFY COLUMN `source_type` varchar(32) NOT NULL COMMENT '来源类型',
    MODIFY COLUMN `source_id` bigint NULL COMMENT '来源ID',
    MODIFY COLUMN `notice_type` varchar(32) NOT NULL COMMENT '通知类型',
    MODIFY COLUMN `notice_level` varchar(16) NOT NULL DEFAULT 'warning' COMMENT '通知级别 warning警告',
    MODIFY COLUMN `notice_status` varchar(32) NOT NULL DEFAULT 'pending' COMMENT '处理状态 pending待处理',
    MODIFY COLUMN `notice_message` varchar(500) NOT NULL COMMENT '通知内容',
    MODIFY COLUMN `suggested_action` varchar(500) NULL COMMENT '建议处理动作',
    MODIFY COLUMN `resolved_at` datetime NULL COMMENT '处理时间',
    MODIFY COLUMN `resolved_by` bigint NULL COMMENT '处理人ID',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_enrollment`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `student_id` bigint NOT NULL COMMENT '学员ID',
    MODIFY COLUMN `class_id` bigint NOT NULL COMMENT '班级ID',
    MODIFY COLUMN `enrollment_no` varchar(64) NOT NULL COMMENT '报名单号',
    MODIFY COLUMN `channel` varchar(32) NULL COMMENT '报名渠道',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'submitted' COMMENT '报名状态 submitted已提交',
    MODIFY COLUMN `enrolled_at` date NOT NULL COMMENT '报名日期',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_attendance`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `class_id` bigint NOT NULL COMMENT '班级ID',
    MODIFY COLUMN `lesson_id` bigint NULL COMMENT '课次ID',
    MODIFY COLUMN `student_id` bigint NOT NULL COMMENT '学员ID',
    MODIFY COLUMN `attendance_date` date NOT NULL COMMENT '考勤日期',
    MODIFY COLUMN `attendance_status` varchar(32) NOT NULL DEFAULT 'present' COMMENT '出勤状态 present出勤',
    MODIFY COLUMN `source` varchar(32) NOT NULL DEFAULT 'manual' COMMENT '数据来源 manual手工',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

-- =========================================================
-- 4. 排课
-- =========================================================

ALTER TABLE `jp_time_slot`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `campus_id` bigint NULL COMMENT '校区ID 空表示机构级',
    MODIFY COLUMN `slot_code` varchar(64) NOT NULL COMMENT '时段编码',
    MODIFY COLUMN `slot_name` varchar(128) NOT NULL COMMENT '时段名称',
    MODIFY COLUMN `weekday` tinyint NOT NULL COMMENT '星期几 1周一-7周日',
    MODIFY COLUMN `start_time` time NOT NULL COMMENT '开始时间',
    MODIFY COLUMN `end_time` time NOT NULL COMMENT '结束时间',
    MODIFY COLUMN `duration_minutes` int NOT NULL COMMENT '时长分钟',
    MODIFY COLUMN `slot_type` varchar(32) NOT NULL DEFAULT 'teaching' COMMENT '时段类型 teaching授课',
    MODIFY COLUMN `applicable_scene` varchar(32) NULL COMMENT '适用场景',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态 active启用',
    MODIFY COLUMN `sort_no` int NULL COMMENT '排序号',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `schedule_lesson`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `campus_id` bigint NOT NULL COMMENT '校区ID',
    MODIFY COLUMN `term_id` bigint NOT NULL COMMENT '学期ID',
    MODIFY COLUMN `class_id` bigint NOT NULL COMMENT '班级ID',
    MODIFY COLUMN `classroom_id` bigint NOT NULL COMMENT '教室ID',
    MODIFY COLUMN `time_slot_id` bigint NOT NULL COMMENT '时段ID',
    MODIFY COLUMN `lesson_date` date NOT NULL COMMENT '上课日期',
    MODIFY COLUMN `lesson_weekday` tinyint NOT NULL COMMENT '星期几 1周一-7周日',
    MODIFY COLUMN `start_time` time NOT NULL COMMENT '开始时间',
    MODIFY COLUMN `end_time` time NOT NULL COMMENT '结束时间',
    MODIFY COLUMN `lesson_title` varchar(128) NULL COMMENT '课题/课次标题',
    MODIFY COLUMN `lesson_status` varchar(32) NOT NULL DEFAULT 'scheduled' COMMENT '课次状态 scheduled已排课',
    MODIFY COLUMN `change_type` varchar(32) NULL COMMENT '调课类型',
    MODIFY COLUMN `related_lesson_id` bigint NULL COMMENT '关联课次ID',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `schedule_teacher_rel`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `lesson_id` bigint NOT NULL COMMENT '课次ID',
    MODIFY COLUMN `teacher_id` bigint NOT NULL COMMENT '教师ID',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

-- =========================================================
-- 5. 账号权限
-- =========================================================

ALTER TABLE `jp_user_account`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID',
    MODIFY COLUMN `default_campus_id` bigint NULL COMMENT '默认校区ID',
    MODIFY COLUMN `username` varchar(64) NOT NULL COMMENT '登录用户名',
    MODIFY COLUMN `password_hash` varchar(255) NOT NULL COMMENT '密码哈希',
    MODIFY COLUMN `display_name` varchar(128) NOT NULL COMMENT '显示名称',
    MODIFY COLUMN `phone` varchar(32) NULL COMMENT '手机号',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态 active启用',
    MODIFY COLUMN `password_reset_required` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否需要重置密码 1是 0否',
    MODIFY COLUMN `password_updated_at` datetime NULL COMMENT '密码更新时间',
    MODIFY COLUMN `force_change_password` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否强制改密 1是 0否',
    MODIFY COLUMN `last_login_at` datetime NULL COMMENT '上次登录时间',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_role`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `role_code` varchar(64) NOT NULL COMMENT '角色编码',
    MODIFY COLUMN `role_name` varchar(128) NOT NULL COMMENT '角色名称',
    MODIFY COLUMN `permission_codes` text NOT NULL COMMENT '权限码列表',
    MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态 active启用',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_user_role_rel`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID',
    MODIFY COLUMN `role_id` bigint NOT NULL COMMENT '角色ID',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
                                                                                  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

ALTER TABLE `jp_user_token`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID',
    MODIFY COLUMN `access_token` varchar(128) NOT NULL COMMENT '访问令牌',
    MODIFY COLUMN `expires_at` datetime NOT NULL COMMENT '过期时间',
    MODIFY COLUMN `revoked_at` datetime NULL COMMENT '撤销时间',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                                                  MODIFY COLUMN `updated_by` bigint NULL