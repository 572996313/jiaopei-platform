-- ============================================================================
-- V120：第三方登录渠道配置 + 通用第三方身份绑定（教培侧执行）
--
-- 双轨：底座基线侧内容一字不差，见
--   backend/cornerstone-core/src/main/resources/cornerstone/migration/V122__create_auth_provider_and_user_identity.sql
-- 种子数据单独放 V122（教培侧多了微信小程序渠道行）；存量微信绑定迁移在 V121。
-- ============================================================================

CREATE TABLE sys_auth_provider (
    id BIGINT NOT NULL COMMENT '主键(雪花ID)',
    provider_code VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道编码(登录页与回调路径使用,如 linuxdo/wechat_miniapp)',
    provider_name VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道名称(登录页展示)',
    provider_type VARCHAR(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '协议实现类型(OAUTH2=标准OAuth2,WECHAT_MINIAPP=微信小程序)',
    icon VARCHAR(64) COLLATE utf8mb4_unicode_ci NULL COMMENT '图标标识(前端按标识选内置图标)',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号(登录页展示顺序)',
    status VARCHAR(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
    client_id VARCHAR(128) COLLATE utf8mb4_unicode_ci NULL COMMENT '客户端ID',
    client_secret VARCHAR(512) COLLATE utf8mb4_unicode_ci NULL COMMENT '客户端密钥(AES-GCM密文,enc:v1: 前缀)',
    authorize_url VARCHAR(512) COLLATE utf8mb4_unicode_ci NULL COMMENT '授权地址',
    token_url VARCHAR(512) COLLATE utf8mb4_unicode_ci NULL COMMENT '令牌地址',
    userinfo_url VARCHAR(512) COLLATE utf8mb4_unicode_ci NULL COMMENT '用户信息地址',
    scope VARCHAR(255) COLLATE utf8mb4_unicode_ci NULL COMMENT '授权范围(多个用空格分隔)',
    token_auth_method VARCHAR(16) COLLATE utf8mb4_unicode_ci NULL COMMENT '令牌请求客户端认证方式(post=表单,basic=HTTP Basic)',
    redirect_uri VARCHAR(512) COLLATE utf8mb4_unicode_ci NULL COMMENT '回调地址(需先在渠道后台登记)',
    extra_config TEXT COLLATE utf8mb4_unicode_ci NULL COMMENT '渠道扩展配置JSON(用户字段映射/webBaseUrl)',
    remark VARCHAR(255) COLLATE utf8mb4_unicode_ci NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    UNIQUE KEY `uk_sys_auth_provider_code` (provider_code, deleted),
    KEY idx_sys_auth_provider_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方登录渠道配置';

CREATE TABLE sys_user_identity (
    id BIGINT NOT NULL COMMENT '主键(雪花ID)',
    provider_code VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道编码(sys_auth_provider.provider_code)',
    app_type VARCHAR(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道内应用类型(miniapp=小程序,mp=公众号,website=网站应用)',
    open_id VARCHAR(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道内唯一用户标识(微信openid/LinuxDo用户ID)',
    union_id VARCHAR(128) COLLATE utf8mb4_unicode_ci NULL COMMENT '跨应用统一标识(如微信unionId,绑定开放平台后才有值)',
    user_id BIGINT NOT NULL COMMENT '绑定的系统账号ID(sys_user_account.id)',
    nickname VARCHAR(128) COLLATE utf8mb4_unicode_ci NULL COMMENT '外部昵称快照',
    avatar_url VARCHAR(512) COLLATE utf8mb4_unicode_ci NULL COMMENT '外部头像地址快照',
    last_login_at DATETIME NULL COMMENT '最近一次通过该身份登录时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    UNIQUE KEY `uk_sys_user_identity` (provider_code, app_type, open_id, user_id, deleted),
    KEY idx_sys_user_identity_user (user_id),
    KEY idx_sys_user_identity_union (provider_code, union_id),
    CONSTRAINT fk_sys_user_identity_user FOREIGN KEY (user_id) REFERENCES sys_user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方身份绑定关系(同一外部身份可绑定多个账号,登录时选账号)';
