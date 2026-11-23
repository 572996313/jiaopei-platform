-- ============================================================================
-- 删除死配置 auth.password.force_change_on_first_login（“首次登录强制改密”）。
--
-- 双轨：本文件是教培侧真正执行的那份；底座基线侧内容一字不差，见
--   backend/cornerstone-core/src/main/resources/cornerstone/migration/V118__remove_dead_force_change_on_first_login_config.sql
-- 教培库只跑 app 侧 V1xx，下游从零建库只跑底座 V1xx，两份都要有。
--
-- 为什么删：
--   1. 该键从 V14（教培）/ V101（底座）种下后，Java 侧没有任何代码读取它；
--   2. 它是 boolean 类型、系统参数页可见可改，用户会误以为关掉它就能停止
--      “初始化密码”提示，实际毫无效果（这类“看起来能配、其实不生效”的开关
--      比没有开关更糟：排查时会误以为是缓存/没保存）；
--   3. 是否强制改密只由 auth.password.force_change_enabled 总开关 + 账号级
--      password_reset_required / force_change_password 标记决定，与这个键无关。
--      新建用户/管理员重置密码时 SystemUserServiceImpl 无条件打标记。
--
-- 如果将来真要做“首次登录不强制改密”，必须同时改后端 createUser/resetPassword
-- 逻辑，不能再靠种一条没人读的配置充数。
--
-- 幂等：按 config_key 删，手工补跑也不会报错。
-- ============================================================================

DELETE FROM `sys_system_config`
WHERE `config_key` = 'auth.password.force_change_on_first_login';
