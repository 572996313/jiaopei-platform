-- 为小程序相关关联表补充中文表注释与字段注释(表名/字段名本身无自述性,必须依赖 COMMENT)

-- jp_wechat_binding:微信 openid 与系统用户的绑定关系
ALTER TABLE `jp_wechat_binding` COMMENT = '微信小程序绑定关系(openid与系统用户)';

ALTER TABLE `jp_wechat_binding`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `openid` varchar(64) NOT NULL COMMENT '微信openid(同一个小程序内唯一)',
    MODIFY COLUMN `union_id` varchar(64) NULL COMMENT '微信unionId(绑定微信开放平台后才有值)',
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '绑定的系统用户ID(jp_user_account.id)',
    MODIFY COLUMN `last_login_at` datetime NULL COMMENT '最近一次微信登录时间',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
    MODIFY COLUMN `deleted` tinyint(1) NOT NULL COMMENT '逻辑删除标识(0=未删除,1=已删除)';

-- jp_user_student_rel:用户与学员档案的关联(家长可绑多个学员,学生绑自己)
ALTER TABLE `jp_user_student_rel` COMMENT = '用户-学员档案关联(学生/家长绑定学员)';

ALTER TABLE `jp_user_student_rel`
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `institution_id` bigint NOT NULL COMMENT '机构ID(多租户隔离)',
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID(jp_user_account.id)',
    MODIFY COLUMN `student_id` bigint NOT NULL COMMENT '学员ID(jp_student.id)',
    MODIFY COLUMN `remark` varchar(500) NULL COMMENT '备注(如"大儿子"等关系描述)',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
    MODIFY COLUMN `deleted` tinyint(1) NOT NULL COMMENT '逻辑删除标识(0=未删除,1=已删除)';