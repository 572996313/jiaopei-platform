-- ============================================================================
-- 密码策略默认值改为「省事档」：不强制改密、强度 simple、最小长度 6。
--
-- 双轨：本文件是教培侧真正执行的那份；底座基线侧 UPDATE 内容一字不差，见
--   backend/cornerstone-core/src/main/resources/cornerstone/migration/V124__simplify_password_policy_defaults.sql
-- 教培库只跑 app 侧 V1xx，下游从零建库只跑底座 V1xx，两份都要有。
--
-- 改了什么（对应「系统管理 → 系统参数」里 auth 组这四项）：
--   1. auth.password.force_change_enabled：true → false，
--      关闭强制改密总开关。新建账号/管理员重置密码时仍会打账号级标记，
--      但总开关关闭后 StubAuthenticationFilter 不再拦截，/auth/me 的
--      forceChangePassword 也返回 false，前端不会再弹改密横幅。
--   2. auth.password.strength_level：medium → simple，
--      只校验长度，不再要求「数字 + 字母」组合。
--   3. auth.password.min_length：8 → 6，初始口令与日常改密都更好记。
--   4. auth.password.max_length 保持 128 不变（上限放宽没有风险，不必收紧）。
--   同时更新 default_value，系统参数页展示的「默认值」与生效值一致；
--   strength_level 的说明文案改为描述组成要求，避免旧文案里的「6-20 位」
--   与实际长度配置脱节。
--
-- 注意：这里是有意覆盖当前值。已经手工调过这几项的库会回到新默认口径，
-- 需要旧口径的在系统参数页改回去即可（这几项 is_system=1，只能改不能删）。
--
-- 幂等：按 config_key 更新，重复执行结果一致。
-- ============================================================================

UPDATE `sys_system_config`
SET `config_value` = 'false',
    `default_value` = 'false',
    `updated_at` = NOW()
WHERE `config_key` = 'auth.password.force_change_enabled';

UPDATE `sys_system_config`
SET `config_value` = 'simple',
    `default_value` = 'simple',
    `description` = '密码组成要求：simple=不限组成 | medium=数字+字母 | complex=大小写字母+数字+特殊字符至少三类；长度由「密码最小长度」「密码最大长度」控制',
    `updated_at` = NOW()
WHERE `config_key` = 'auth.password.strength_level';

UPDATE `sys_system_config`
SET `config_value` = '6',
    `default_value` = '6',
    `updated_at` = NOW()
WHERE `config_key` = 'auth.password.min_length';
