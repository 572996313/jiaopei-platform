-- V65：公告中心的两张表 + 公告类型字典
-- 口径（2026-09-26 与用户逐条定稿）：公告 = 管理员人工撰写、选范围广播、可撤回的单向广播，
-- 与系统自动产生的事件提醒（jp_class_change_notice 那类班级主档待办）分家，本表不装事件。
-- 表名带 jp_msg_ 标识：全仓业务表按 jp_ 前缀平铺，光看 jp_announcement 认不出这是消息域，
-- 加名词段后在表清单与 docs/核心实体清单.md 里自动排在一起。字典码与权限码不带这个前缀。
-- 两张表的理由：阅读进度必须按人算（谁读了、多少人没读），故主档与接收人拆开；
-- 接收人在发布瞬间按范围解析并落库，receiver_total 即当时的分母，之后人员进出不改分母。
-- 一期只做站内：无定时发布（全仓零 @Scheduled）、无微信渠道，所以不留 scheduled_at/channel 这类空列。

CREATE TABLE jp_msg_announcement (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    institution_id BIGINT NOT NULL COMMENT '机构ID(多租户隔离,取自发布人登录态,接口不接受该参数)',
    title VARCHAR(120) NOT NULL COMMENT '公告标题',
    content TEXT NOT NULL COMMENT '正文(发布时冻结的文本快照,落库后不随班级改名等主档变更漂移)',
    type_code VARCHAR(32) NOT NULL COMMENT '公告类型字典(announcement_type:holiday=放假安排,schedule_change=教务调整,teaching_notice=教学通知,urgent=紧急)',
    status VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT '公告状态(draft=草稿,published=已发布,recalled=已撤回;由发布/撤回动作判定,不入字典)',
    top_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶(0=否,1=是;置顶在接收端排最前,撤回即取消)',
    audience_codes VARCHAR(64) NOT NULL COMMENT '接收对象(逗号分隔:teacher=教师,parent=学员家长,account=全部登录账号)',
    campus_scope VARCHAR(16) NOT NULL DEFAULT 'all' COMMENT '校区范围(all=全部校区,part=指定校区)',
    campus_ids VARCHAR(1024) NULL COMMENT '指定校区ID列表(campus_scope=part时生效,逗号分隔)',
    class_ids VARCHAR(1024) NULL COMMENT '限定班级ID列表(逗号分隔,空=按校区全发)',
    receiver_total INT NOT NULL DEFAULT 0 COMMENT '接收人总数(发布时解析出的范围快照,阅读进度的固定分母)',
    published_at DATETIME NULL COMMENT '发布时间(未发布为NULL)',
    published_by BIGINT NULL COMMENT '发布人账号ID(sys_user_account.id)',
    recalled_at DATETIME NULL COMMENT '撤回时间(未撤回为NULL)',
    recalled_by BIGINT NULL COMMENT '撤回人账号ID',
    recall_reason VARCHAR(200) NULL COMMENT '撤回原因(操作留痕,列表行内展示)',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    KEY idx_jp_msg_announcement_list (institution_id, status, created_at),
    CONSTRAINT chk_jp_msg_announcement_status CHECK (status IN ('draft', 'published', 'recalled')),
    CONSTRAINT fk_jp_msg_announcement_institution FOREIGN KEY (institution_id) REFERENCES sys_institution (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告主档(人工撰写、按范围广播、可撤回的站内公告)';

CREATE TABLE jp_msg_announcement_receiver (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    institution_id BIGINT NOT NULL COMMENT '机构ID(多租户隔离,冗余自主档便于按人查未读)',
    announcement_id BIGINT NOT NULL COMMENT '公告ID(jp_msg_announcement.id)',
    user_id BIGINT NOT NULL COMMENT '接收人账号ID(sys_user_account.id)',
    audience_code VARCHAR(16) NOT NULL COMMENT '该接收人因哪一档范围命中(teacher=教师,parent=学员家长,account=全部登录账号)',
    read_at DATETIME NULL COMMENT '已读时间(未读为NULL;重复打开只记首次)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_msg_announcement_receiver (announcement_id, user_id, deleted),
    KEY idx_jp_msg_receiver_user (user_id, read_at),
    KEY idx_jp_msg_receiver_read (announcement_id, read_at),
    CONSTRAINT fk_jp_msg_receiver_announcement FOREIGN KEY (announcement_id) REFERENCES jp_msg_announcement (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告接收人(发布时按范围解析展开,一行一人,记已读时间)';

-- 公告类型入系统字典：取值可枚举、给发布人选择、运维可能扩值，AGENTS「枚举值/字典规范」要求以字典为唯一事实源。
-- status 不入字典（草稿/已发布/已撤回由发布与撤回动作的代码分支判定，进字典等于允许人在页面上改掉判定逻辑依赖的值）。
INSERT IGNORE INTO sys_dict_type (
    id, type_code, type_name, status, remark, deleted
) VALUES
    (14, 'announcement_type', '公告类型', 'active', '公告中心-发布类型选项', 0);

INSERT IGNORE INTO sys_dict_item (
    id, type_id, item_code, item_name, item_value, sort_no, status, remark, deleted
) VALUES
    (72, 14, 'holiday', '放假安排', 'holiday', 10, 'active', NULL, 0),
    (73, 14, 'schedule_change', '教务调整', 'schedule_change', 20, 'active', NULL, 0),
    (74, 14, 'teaching_notice', '教学通知', 'teaching_notice', 30, 'active', NULL, 0),
    (75, 14, 'urgent', '紧急', 'urgent', 40, 'active', NULL, 0);
