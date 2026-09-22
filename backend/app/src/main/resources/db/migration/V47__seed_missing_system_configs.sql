-- V47: 补齐"代码在读、库里无种子"的系统参数（教培项目专属，第二个项目按自己的模块决定要不要带）
-- 1) auth.register.default_institution_id — 小程序自助注册账号的默认归属机构,
--    MiniappServiceImpl / WechatAuthServiceImpl 读取;此前只有代码默认值 1,系统参数页看不见也改不了。
-- 2) miniapp.register.page — 邀请码小程序码扫码直达的页面路径,MiniappQrcodeServiceImpl 读取;
--    页面改版时在这里调整,不用发版。is_system=1 禁止删除,只许改值。
INSERT IGNORE INTO sys_system_config (id, config_key, config_value, value_type, config_group, display_name, description, default_value, is_system, sort_order, status, deleted) VALUES
(9, 'auth.register.default_institution_id', '1', 'int', 'auth', '小程序注册默认机构ID', '微信小程序自助注册账号默认归属的机构(institution_id)。多机构租户下自助注册无法指定机构时的兜底归属', '1', 1, 85, 'active', 0),
(10, 'miniapp.register.page', 'pages/register/index', 'string', 'miniapp', '小程序注册页路径', '生成邀请码小程序码(wxacode)后扫码直达的小程序页面路径,页面改版时在此修改', 'pages/register/index', 1, 10, 'active', 0);
