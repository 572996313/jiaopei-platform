-- V67：公告模板表
-- 口径：模板是"新建公告时预填草稿"的可复用骨架（标题/正文/类型/接收范围），不是公告的一种，
-- 所以不往 jp_msg_announcement 加 template_id 之类的溯源列——发布出去的公告与手写公告没有任何差别，
-- 加溯源列只会让"模板被删后历史公告怎么办"变成新问题。
-- 表名带 jp_msg_ 前缀，与 V65 的两张公告表同域，理由同彼处注释。
-- 权限沿用公告中心那一对节点（V66：页面 announcement_center + 按钮 announcement_write），
-- 模板维护与公告维护是同一页的同一能力，按 AGENTS「同一个码在同一页面只挂一处」不再加节点。
-- 不建唯一键：模板名称重名无害（收件人只是在下拉里多看到一条），
-- 而建了唯一键就要面对软删行永久占名的老问题（见全站 uk_jp_* 不含 deleted 的欠账）。

CREATE TABLE jp_msg_announcement_template (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    institution_id BIGINT NOT NULL COMMENT '机构ID(多租户隔离,取自建模板的人的登录态,接口不接受该参数)',
    name VARCHAR(60) NOT NULL COMMENT '模板名称(在套用下拉与模板维护列表里展示,仅用于区分,不强制唯一)',
    title VARCHAR(120) NOT NULL COMMENT '预填的公告标题',
    content TEXT NOT NULL COMMENT '预填的公告正文(套用时原样填进表单,不做变量渲染)',
    type_code VARCHAR(32) NOT NULL COMMENT '公告类型字典(announcement_type:holiday=放假安排,schedule_change=教务调整,teaching_notice=教学通知,urgent=紧急)',
    top_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶(0=否,1=是;套用时带进表单)',
    audience_codes VARCHAR(64) NOT NULL COMMENT '预填接收对象(逗号分隔:teacher=教师,parent=学员家长,account=全部登录账号;留空=套用时再选)',
    campus_scope VARCHAR(16) NOT NULL DEFAULT 'all' COMMENT '校区范围(all=全部校区,part=指定校区)',
    campus_ids VARCHAR(1024) NULL COMMENT '指定校区ID列表(campus_scope=part时生效,逗号分隔)',
    class_ids VARCHAR(1024) NULL COMMENT '限定班级ID列表(逗号分隔,空=按校区全发)',
    remark VARCHAR(500) NULL COMMENT '备注(模板使用说明,只在管理端可见,不进公告正文)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    KEY idx_jp_msg_announcement_template_list (institution_id, created_at),
    CONSTRAINT fk_jp_msg_announcement_template_institution FOREIGN KEY (institution_id) REFERENCES sys_institution (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告模板(新建公告时预填的可复用骨架)';
