-- V124：站内事件流（事件账本 + 订阅 + 配置化巡检）
--
-- 口径（2026-11-26 与用户定稿）：
--   1. 事件产生时不筛人，读取时按「可见范围 + 个人订阅」筛；订阅管注意力，不管权限。
--   2. 事件类型由「数据源视图 + 类型配置」驱动，运维在页面加类型/调阈值/停文案，不改代码；
--      只有新的数据形状（新视图）才需要开发写迁移。视图只返回候选行，条件由配置里的
--      [{column,op,value}] 白名单比较执行，不开任意 SQL 输入口。
--   3. 公告（jp_msg_announcement）保持原样：人工撰写、按范围广播、可撤回，本批不往里装事件；
--      事件流页把公告作为一个只读 tab 桥接展示。
--   4. 系统派生事件的状态由条件决定（条件消失自动 resolved）；人工事项（任务/反馈/问题/提醒）
--      才由人流转 open/in_progress/done/closed。事件不删，只按保留期清理。
--   5. 校区范围先不拦：message.event.campus-scope.enabled=false，权限口径确定后改 true 即生效，
--      查询与可见性代码已按 CampusScopeGuard 接好。
--
-- 兼容自查：普通 DDL + 两条只读视图；无 CTE、无窗口函数、无 JSON_TABLE、无 IF( 函数；
-- 所有字符列显式 COLLATE=utf8mb4_unicode_ci；表/列全部中文 COMMENT。
-- 视图只做 SELECT，生产 Flyway 账号需要 CREATE VIEW 权限（与建表同属 DDL）。

-- ---------------------------------------------------------------------------
-- 1) 数据源视图目录：开发登记视图，运维只在视图上配事件类型
-- ---------------------------------------------------------------------------
CREATE TABLE jp_msg_source (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    source_code VARCHAR(64) NOT NULL COMMENT '数据源代码(代码判定,不入字典)',
    display_name VARCHAR(64) NOT NULL COMMENT '数据源名称',
    view_name VARCHAR(128) NOT NULL COMMENT '只读视图名(白名单,只能选已登记的视图)',
    description VARCHAR(500) NULL COMMENT '说明(返回哪些行、列契约)',
    column_contract TEXT NULL COMMENT '列契约(最近一次校验发现的列,逗号分隔)',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用(0=否,1=是)',
    last_validated_at DATETIME NULL COMMENT '最近一次列校验时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_msg_source_code (source_code, deleted),
    UNIQUE KEY uk_jp_msg_source_view (view_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件数据源视图目录(开发登记,运维只在其上配类型)';

-- ---------------------------------------------------------------------------
-- 2) 事件类型目录：运维可增停、调阈值/文案/级别/可见性，不改代码
-- ---------------------------------------------------------------------------
CREATE TABLE jp_msg_event_type (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    type_code VARCHAR(64) NOT NULL COMMENT '类型码(代码/配置判定,不入字典)',
    type_name VARCHAR(64) NOT NULL COMMENT '类型名称',
    category VARCHAR(32) NOT NULL COMMENT '分类字典(message_category:teaching=教学,fee=收费,collaboration=协作,system=系统)',
    source_mode VARCHAR(16) NOT NULL DEFAULT 'manual' COMMENT '产生方式(scan=配置化巡检,manual=人工,api=接口发布;代码判定,不入字典)',
    source_code VARCHAR(64) NULL COMMENT 'scan 时指向 jp_msg_source.source_code',
    condition_json TEXT NULL COMMENT '过滤条件 JSON:[{column,op,value}],只允许视图列+比较符,不是 SQL',
    title_template VARCHAR(200) NOT NULL COMMENT '标题模板(${列名} 取自视图行)',
    body_template TEXT NOT NULL COMMENT '正文模板(纯文本快照,不存 HTML)',
    action_template VARCHAR(500) NULL COMMENT '深链模板(${列名});渲染后按白名单校验',
    dedupe_template VARCHAR(300) NOT NULL COMMENT '去重键模板(${列名});同一业务对象同一问题只保留一条',
    level VARCHAR(16) NOT NULL DEFAULT 'info' COMMENT '级别(info/warning/critical;代码判定,不入字典)',
    visibility VARCHAR(16) NOT NULL DEFAULT 'internal' COMMENT '可见端(internal=内部,parent=家长,student=学生;代码判定,不入字典)',
    interval_minutes INT NOT NULL DEFAULT 5 COMMENT '巡检间隔(分钟,scan 生效)',
    retention_days INT NOT NULL DEFAULT 90 COMMENT '保留天数',
    creatable TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可在「新建事项」里手选(0=否,如提醒类内部类型)',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用(0=否,1=是)',
    last_run_at DATETIME NULL COMMENT '最近一次巡检时间',
    last_run_count INT NOT NULL DEFAULT 0 COMMENT '最近一次巡检命中行数',
    last_error VARCHAR(500) NULL COMMENT '最近一次巡检失败原因(成功为NULL)',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_msg_event_type_code (type_code, deleted),
    KEY idx_jp_msg_event_type_scan (source_mode, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件类型目录(运维配置,加类型不改代码)';

-- ---------------------------------------------------------------------------
-- 3) 事件账本：一次发生/成立一行，产生时不筛人
-- ---------------------------------------------------------------------------
CREATE TABLE jp_msg_event (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    institution_id BIGINT NOT NULL COMMENT '机构ID(多租户隔离)',
    campus_id BIGINT NULL COMMENT '校区ID(锚点,可空)',
    type_code VARCHAR(64) NOT NULL COMMENT '事件类型码(jp_msg_event_type.type_code)',
    category VARCHAR(32) NOT NULL COMMENT '分类字典(message_category)',
    level VARCHAR(16) NOT NULL DEFAULT 'info' COMMENT '级别(info/warning/critical)',
    title VARCHAR(200) NOT NULL COMMENT '标题(模板渲染后的纯文本快照)',
    body TEXT NOT NULL COMMENT '正文(模板渲染后的纯文本快照)',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态(系统:active/resolved/expired;人工:open/in_progress/done/closed)',
    anchor_class_id BIGINT NULL COMMENT '班级锚点(可空)',
    anchor_student_id BIGINT NULL COMMENT '学员锚点(可空)',
    anchor_teacher_id BIGINT NULL COMMENT '教师锚点(可空)',
    anchor_lesson_id BIGINT NULL COMMENT '课次锚点(可空)',
    ref_type VARCHAR(64) NULL COMMENT '来源单据类型(如 schedule_lesson/jp_fee_ledger)',
    ref_id BIGINT NULL COMMENT '来源单据ID',
    dedupe_key VARCHAR(300) NOT NULL COMMENT '去重键(同一业务对象同一问题幂等)',
    occurred_at DATETIME NOT NULL COMMENT '发生/成立时间',
    expire_at DATETIME NULL COMMENT '过期时间(空=不自动过期)',
    resolved_at DATETIME NULL COMMENT '消解/完成时间(系统事件由条件消失写入,人工事项由状态流转写入)',
    resolved_by BIGINT NULL COMMENT '消解/完成人账号ID',
    assignee_user_id BIGINT NULL COMMENT '指派人账号ID(人工事项/提醒用)',
    due_at DATETIME NULL COMMENT '截止时间(人工事项用)',
    action_url VARCHAR(500) NULL COMMENT '深链(渲染后按白名单校验,空=无跳转)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_msg_event_dedupe (institution_id, type_code, dedupe_key, deleted),
    KEY idx_jp_msg_event_stream (institution_id, status, occurred_at),
    KEY idx_jp_msg_event_campus (campus_id, status, occurred_at),
    KEY idx_jp_msg_event_class (anchor_class_id, status, occurred_at),
    KEY idx_jp_msg_event_student (anchor_student_id, status, occurred_at),
    KEY idx_jp_msg_event_teacher (anchor_teacher_id, status, occurred_at),
    KEY idx_jp_msg_event_assignee (assignee_user_id, status, occurred_at),
    KEY idx_jp_msg_event_type (type_code, status, occurred_at),
    CONSTRAINT fk_jp_msg_event_institution FOREIGN KEY (institution_id) REFERENCES sys_institution (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件账本(系统巡检与人工事项共用,产生时不筛人)';

-- ---------------------------------------------------------------------------
-- 4) 已读：按人记，一个人读了不影响别人
-- ---------------------------------------------------------------------------
CREATE TABLE jp_msg_event_read (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    user_id BIGINT NOT NULL COMMENT '账号ID(sys_user_account.id)',
    event_id BIGINT NOT NULL COMMENT '事件ID(jp_msg_event.id)',
    read_at DATETIME NOT NULL COMMENT '首次已读时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_msg_event_read (user_id, event_id, deleted),
    KEY idx_jp_msg_event_read_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件已读(按人一行,重复打开只记首次)';

-- ---------------------------------------------------------------------------
-- 5) 个人订阅：从全量流里取自己关心的部分，管注意力不管权限
-- ---------------------------------------------------------------------------
CREATE TABLE jp_msg_subscription (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    user_id BIGINT NOT NULL COMMENT '账号ID(sys_user_account.id)',
    rule_type VARCHAR(16) NOT NULL COMMENT '规则维度(type_code/category/campus/class/student/teacher/level/keyword;代码判定,不入字典)',
    rule_value VARCHAR(128) NOT NULL COMMENT '规则值(维度对应的ID或关键字)',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用(0=否,1=是)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_jp_msg_subscription (user_id, rule_type, rule_value, deleted),
    KEY idx_jp_msg_subscription_user (user_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人事件订阅规则(同维度OR,跨维度AND;空规则=全部可见事件)';

-- ---------------------------------------------------------------------------
-- 6) 处理时间线：人工事项的评论/指派/状态流转/补录
-- ---------------------------------------------------------------------------
CREATE TABLE jp_msg_event_log (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    institution_id BIGINT NOT NULL COMMENT '机构ID(多租户隔离)',
    event_id BIGINT NOT NULL COMMENT '事件ID(jp_msg_event.id)',
    user_id BIGINT NULL COMMENT '操作人账号ID',
    action VARCHAR(32) NOT NULL COMMENT '动作(create/comment/assign/status/backfill;代码判定,不入字典)',
    content VARCHAR(1000) NULL COMMENT '内容(评论正文或状态说明)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    KEY idx_jp_msg_event_log_event (event_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件处理时间线(人工事项用)';

-- ---------------------------------------------------------------------------
-- 7) 两张只读视图：视图只返回候选行，条件/阈值在类型配置里
-- ---------------------------------------------------------------------------
-- 课次执行：返回近 30 天可点名的课次及点名进度。missing_count = 应到 - 已点。
-- 视图窗口即"可跟踪窗口"：更早的课次不再进入候选，旧事件由执行器按本轮未命中自动 resolved。
CREATE OR REPLACE VIEW v_msg_lesson_execution AS
SELECT
    l.institution_id AS institution_id,
    l.campus_id AS campus_id,
    l.class_id AS class_id,
    l.id AS lesson_id,
    'schedule_lesson' AS ref_type,
    l.id AS ref_id,
    c.class_name AS class_name,
    l.lesson_date AS lesson_date,
    l.start_time AS start_time,
    l.end_time AS end_time,
    l.lesson_status AS lesson_status,
    COUNT(DISTINCT cs.student_id) AS expected,
    COUNT(DISTINCT a.student_id) AS marked,
    COUNT(DISTINCT cs.student_id) - COUNT(DISTINCT a.student_id) AS missing_count,
    TIMESTAMPDIFF(MINUTE, TIMESTAMP(l.lesson_date, l.start_time), NOW()) AS minutes_since_start,
    TIMESTAMPDIFF(MINUTE, TIMESTAMP(l.lesson_date, l.end_time), NOW()) AS minutes_since_end
FROM schedule_lesson l
JOIN jp_class c ON c.id = l.class_id AND c.deleted = 0
LEFT JOIN jp_class_student cs
    ON cs.class_id = l.class_id AND cs.deleted = 0 AND cs.status = 'active'
LEFT JOIN jp_attendance a
    ON a.lesson_id = l.id AND a.student_id = cs.student_id AND a.deleted = 0
WHERE l.deleted = 0
  AND l.lesson_date >= CURDATE() - INTERVAL 30 DAY
GROUP BY l.institution_id, l.campus_id, l.class_id, l.id, c.class_name,
         l.lesson_date, l.start_time, l.end_time, l.lesson_status;

-- 台账未收齐：应收 = Σ(收费月数×月标准−上期结余−其他抵扣)，实收 = Σ收款，未收 = 应收−实收。
-- 与 FeeBillingEngine.receivable/unpaid 同一公式；视图只给候选行，是否提醒由类型条件决定。
CREATE OR REPLACE VIEW v_msg_fee_ledger_unpaid AS
SELECT
    l.institution_id AS institution_id,
    l.campus_id AS campus_id,
    l.class_id AS class_id,
    l.student_id AS student_id,
    l.id AS ledger_id,
    'jp_fee_ledger' AS ref_type,
    l.id AS ref_id,
    s.student_name AS student_name,
    c.class_name AS class_name,
    ROUND(SUM(i.billable_months * i.monthly_standard - i.opening_balance - i.other_deduction), 2) AS receivable,
    ROUND(COALESCE(p.paid_amount, 0), 2) AS paid,
    ROUND(SUM(i.billable_months * i.monthly_standard - i.opening_balance - i.other_deduction)
          - COALESCE(p.paid_amount, 0), 2) AS unpaid
FROM jp_fee_ledger l
JOIN jp_student s ON s.id = l.student_id AND s.deleted = 0
JOIN jp_class c ON c.id = l.class_id AND c.deleted = 0
JOIN jp_fee_ledger_item i ON i.ledger_id = l.id AND i.deleted = 0
LEFT JOIN (
    SELECT ledger_id, SUM(amount) AS paid_amount
    FROM jp_fee_payment
    WHERE deleted = 0
    GROUP BY ledger_id
) p ON p.ledger_id = l.id
WHERE l.deleted = 0 AND l.status = 'active'
GROUP BY l.institution_id, l.campus_id, l.class_id, l.student_id, l.id,
         s.student_name, c.class_name, p.paid_amount;

-- ---------------------------------------------------------------------------
-- 8) 字典：消息分类（运维可选、可扩，按 AGENTS 字典边界入字典）
--    status/level/visibility/source_mode 都是代码分支判定值，不入字典。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type (id, type_code, type_name, status, remark, deleted) VALUES
    (16, 'message_category', '消息分类', 'active', '站内事件流的分类选项：教学/收费/协作/系统', 0);

INSERT IGNORE INTO sys_dict_item (id, type_id, item_code, item_name, item_value, sort_no, status, remark, deleted) VALUES
    (81, 16, 'teaching', '教学', 'teaching', 10, 'active', NULL, 0),
    (82, 16, 'fee', '收费', 'fee', 20, 'active', NULL, 0),
    (83, 16, 'collaboration', '协作', 'collaboration', 30, 'active', NULL, 0),
    (84, 16, 'system', '系统', 'system', 40, 'active', NULL, 0);

-- ---------------------------------------------------------------------------
-- 9) 系统参数：校区范围先不拦，权限口径确定后改 true 即生效
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_system_config (
    id, config_key, config_value, value_type, config_group, display_name, description,
    default_value, is_system, sort_order, status, deleted
) VALUES
    (20, 'message.event.campus-scope.enabled', 'false', 'boolean', 'message',
     '事件流按校区范围过滤', 'false=内部员工可见本机构全部事件；true=再按角色校区数据范围收窄。权限口径确定后改 true 即生效',
     'false', 0, 10, 'active', 0);

-- ---------------------------------------------------------------------------
-- 10) 数据源与事件类型种子：2 张视图 + 3 个巡检类型 + 4 个人工类型
--     演示"同一张视图配两个类型、不改代码"：未点名与未结课共用 v_msg_lesson_execution。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO jp_msg_source (
    id, source_code, display_name, view_name, description, column_contract, enabled, deleted
) VALUES
    (1, 'lesson_execution', '课次执行明细', 'v_msg_lesson_execution',
     '近 30 天可点名课次及点名进度；列：institution_id/campus_id/class_id/lesson_id/ref_type/ref_id/class_name/lesson_date/start_time/end_time/lesson_status/expected/marked/missing_count/minutes_since_start/minutes_since_end',
     'institution_id,campus_id,class_id,lesson_id,ref_type,ref_id,class_name,lesson_date,start_time,end_time,lesson_status,expected,marked,missing_count,minutes_since_start,minutes_since_end',
     1, 0),
    (2, 'fee_ledger_unpaid', '台账未收齐', 'v_msg_fee_ledger_unpaid',
     '在收台账的应收/实收/未收；列：institution_id/campus_id/class_id/student_id/ledger_id/ref_type/ref_id/student_name/class_name/receivable/paid/unpaid',
     'institution_id,campus_id,class_id,student_id,ledger_id,ref_type,ref_id,student_name,class_name,receivable,paid,unpaid',
     1, 0);

INSERT IGNORE INTO jp_msg_event_type (
    id, type_code, type_name, category, source_mode, source_code, condition_json,
    title_template, body_template, action_template, dedupe_template,
    level, visibility, interval_minutes, retention_days, creatable, enabled, sort_no, deleted
) VALUES
    (1, 'lesson_attendance_missing', '课次未点名', 'teaching', 'scan', 'lesson_execution',
     '[{"column":"missing_count","op":"gt","value":0},{"column":"minutes_since_start","op":"gte","value":30},{"column":"lesson_status","op":"in","value":"scheduled,rescheduled,makeup"}]',
     '课次未点名',
     '${class_name} ${lesson_date} ${start_time}-${end_time} 未完成点名（应到 ${expected} 人，已点 ${marked} 人）。',
     '/schedule?date=${lesson_date}&focus=pending',
     'attendance_missing:lesson:${lesson_id}',
     'warning', 'internal', 5, 90, 0, 1, 10, 0),
    (2, 'lesson_not_completed', '课次未结课', 'teaching', 'scan', 'lesson_execution',
     '[{"column":"minutes_since_end","op":"gt","value":0},{"column":"lesson_status","op":"in","value":"scheduled,rescheduled,makeup"}]',
     '课次未结课',
     '${class_name} ${lesson_date} ${start_time}-${end_time} 已过下课时间但未结课。',
     '/schedule?date=${lesson_date}&tab=management',
     'lesson_not_completed:lesson:${lesson_id}',
     'warning', 'internal', 5, 90, 0, 1, 20, 0),
    (3, 'fee_ledger_unpaid', '学员费用未收齐', 'fee', 'scan', 'fee_ledger_unpaid',
     '[{"column":"unpaid","op":"gt","value":0}]',
     '学员费用未收齐',
     '${student_name}（${class_name}）应收 ${receivable} 元，实收 ${paid} 元，未收 ${unpaid} 元。',
     '/fee-ledger?ledgerId=${ledger_id}',
     'fee_unpaid:ledger:${ledger_id}',
     'warning', 'internal', 10, 90, 0, 1, 30, 0),
    (4, 'manual_task', '任务', 'collaboration', 'manual', NULL, NULL,
     '任务', '', NULL, '', 'info', 'internal', 0, 365, 1, 1, 40, 0),
    (5, 'manual_feedback', '反馈', 'collaboration', 'manual', NULL, NULL,
     '反馈', '', NULL, '', 'info', 'internal', 0, 365, 1, 1, 50, 0),
    (6, 'manual_issue', '问题', 'collaboration', 'manual', NULL, NULL,
     '问题', '', NULL, '', 'warning', 'internal', 0, 365, 1, 1, 60, 0),
    (7, 'manual_nudge', '提醒', 'collaboration', 'manual', NULL, NULL,
     '有人提醒你查看一条事项', '', '/messages?eventId=${ref_id}', '', 'info', 'internal', 0, 365, 0, 1, 70, 0);

-- ---------------------------------------------------------------------------
-- 11) 菜单与权限
--     读取接口登录即可（沿用 V113 业务读取码清零）；写操作挂按钮节点。
--     message:item:write  人工事项新建/编辑/提醒
--     message:self:write  个人订阅保存（人人可用，授给全部后台角色）
--     system:message:manage 事件目录/数据源/阅读台账（系统设置，平台管理员）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (26, 'admin', NULL, 'message_center', '消息与事项', 'catalog', NULL, NULL,
     NULL, 'ChatDotRound', 50, 1, 'active',
     '事件流与订阅：全量事件、我的订阅、人工事项；目录不挂权限码，入口显隐由子页面节点决定', 0),
    (27, 'admin', 26, 'message_stream', '事件中心', 'menu', '/messages', 'education/MessageCenterView',
     NULL, 'Bell', 10, 1, 'active',
     '全量事件流：所有系统事件与人工事项；登录即可看，订阅只负责收窄注意力', 0),
    (28, 'admin', 26, 'message_subscriptions', '我的订阅', 'menu', '/messages/subscriptions',
     'education/MessageSubscriptionView',
     NULL, 'Star', 20, 1, 'active',
     '按类型/校区/班级/学员/教师/级别/关键字订阅；空规则等于全部可见事件', 0),
    (153, 'admin', 13, 'system_message_type', '事件目录', 'menu', '/system/message-types',
     'education/MessageTypeManagementView',
     'system:message:manage', 'SetUp', 73, 1, 'active',
     '运维维护事件类型与数据源：加类型/调阈值/停文案/调级别/启停，不改代码', 0),
    (154, 'admin', 13, 'system_message_ledger', '事件阅读台账', 'menu', '/system/message-ledger',
     'education/MessageLedgerView',
     'system:message:manage', 'View', 74, 1, 'active',
     '看每个事件类型产量、已读人数与订阅覆盖；漏订阅场景靠这里发现', 0),
    (155, 'admin', 27, 'message_item_write', '事项维护', 'button', NULL, NULL,
     'message:item:write', NULL, 10, 0, 'active',
     '新建/编辑人工事项、提醒某人；勾了即可写', 0),
    (156, 'admin', 28, 'message_self_write', '维护订阅', 'button', NULL, NULL,
     'message:self:write', NULL, 10, 0, 'active',
     '保存个人订阅规则；授给全部后台角色，避免教师订阅自己的班时 403', 0);

-- 目录 + 两张页面 + 两个按钮授给全部后台角色（排除小程序端角色）。
-- id 段：1530000~1539999 已核对为空闲，按「段 + 角色ID」生成，可整脚本重放。
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 1530000 + r.id, r.id, 26, 0
FROM sys_role r
WHERE r.deleted = 0 AND r.status = 'active' AND r.role_code NOT LIKE 'MINIAPP%';

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 1531000 + r.id, r.id, 27, 0
FROM sys_role r
WHERE r.deleted = 0 AND r.status = 'active' AND r.role_code NOT LIKE 'MINIAPP%';

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 1532000 + r.id, r.id, 28, 0
FROM sys_role r
WHERE r.deleted = 0 AND r.status = 'active' AND r.role_code NOT LIKE 'MINIAPP%';

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 1533000 + r.id, r.id, 155, 0
FROM sys_role r
WHERE r.deleted = 0 AND r.status = 'active' AND r.role_code NOT LIKE 'MINIAPP%';

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 1534000 + r.id, r.id, 156, 0
FROM sys_role r
WHERE r.deleted = 0 AND r.status = 'active' AND r.role_code NOT LIKE 'MINIAPP%';

-- 系统设置两张管理页只给平台管理员（沿用 V74 口径：机构运营不进系统设置）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (1535001, 1, 153, 0),
    (1535002, 1, 154, 0);
