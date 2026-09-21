-- V43: 登录页提示语改成系统参数（属教培部署专属，第二个项目不带本脚本）
-- 底座 LoginView 过去把一句含默认管理员口令的提示写死在页面上，换项目就会带出错误文案。
-- 现在提示语统一由 core 读取 auth.login.page_tip（读不到即空串，前端不渲染），
-- 本脚本只把教培项目当前在用的这句话种进来；「配置值」不允许留空，要隐藏提示就在系统参数页删除本行。
INSERT IGNORE INTO sys_system_config (id, config_key, config_value, value_type, config_group, display_name, description, default_value, is_system, sort_order, status, deleted) VALUES
(8, 'auth.login.page_tip', '当前已切真实登录接口。默认首个管理员账号：admin，初始密码：Admin@123456。', 'string', 'auth', '登录页提示语', '展示在管理端登录页表单下方的提示语，删除本参数即不展示。生产环境不要写真实口令', '', 0, 80, 'active', 0);
