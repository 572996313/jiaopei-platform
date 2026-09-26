-- V68: 系统名称与登录页文案改成系统参数（属教培部署专属，第二个项目不带本脚本）
-- 原先这几句写死在壳工程 frontend/src/modules.ts 的 registerBranding 里，改名要重新构建前端。
-- 现在由 core 的 GET /api/system/branding 读取 brand.*（读不到即空串，前端显示"未配置"并指引到这里维护），
-- 本脚本种的是本项目定稿的展示名（原名"教培管理平台"，2026-09-26 改名"课栈"）。
-- 不写进底座 cornerstone/migration/V1xx 基线：项目专有名词进底座会被 CornerstoneBaselineGuardTest 拦住。
-- is_system=0：这四条是展示文案不是内置开关，允许删除；删掉即回到"未配置"状态。
INSERT IGNORE INTO sys_system_config (id, config_key, config_value, value_type, config_group, display_name, description, default_value, is_system, sort_order, status, deleted) VALUES
(11, 'brand.name', '课栈', 'string', 'brand', '系统名称', '横幅左上主标题、窄栏角标首字、登录页引子。改后刷新页面即生效', '', 0, 10, 'active', 0),
(12, 'brand.tagline', '线下班课教务', 'string', 'brand', '定位副标题', '横幅系统名称下方小字、登录页左侧大标题。改后刷新页面即生效', '', 0, 20, 'active', 0),
(13, 'brand.login_copy', '校区、班级、排课、点名与变动提醒，一处管理。', 'string', 'brand', '登录页介绍语', '展示在登录页左侧大标题下方的一句话介绍，删除本参数即不展示', '', 0, 30, 'active', 0),
(14, 'brand.login_form_title', '进入课栈', 'string', 'brand', '登录表单标题', '登录页右侧表单卡的标题', '', 0, 40, 'active', 0);
