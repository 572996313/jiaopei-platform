-- V69: 公告小程序码扫码直达的小程序页面路径（教培项目专属，第二个项目按自己的小程序页面决定要不要带）
-- MiniappQrcodeServiceImpl.createAnnouncementWxacode 读取；此前该服务只有 miniapp.register.page 一个页面参数，
-- 公告码直达页若写死在代码里，页面改版时只能发版。与 V47 的 miniapp.register.page 同口径：
-- is_system=1 禁止删除，只许改值。
INSERT IGNORE INTO sys_system_config (id, config_key, config_value, value_type, config_group, display_name, description, default_value, is_system, sort_order, status, deleted) VALUES
(15, 'miniapp.announcement.page', 'pages/messages/detail', 'string', 'miniapp', '小程序公告详情页路径', '生成公告小程序码(wxacode)后扫码直达的小程序页面路径,页面改版时在此修改', 'pages/messages/detail', 1, 20, 'active', 0);
