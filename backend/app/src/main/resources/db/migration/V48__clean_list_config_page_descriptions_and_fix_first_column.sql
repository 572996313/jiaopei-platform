-- V48: 清理列表配置的"功能罗列型"页面说明,并把配置化列表的首列钉住
--
-- 两件事都是用户反馈的直接结果:
-- 1) "支持创建、编辑、启停、重置密码、分配角色" 这类 description 只是把按钮清单用中文
--    复述一遍,零信息量还占页头位置。口径改为:说明只写"看数据本身看不出来的东西"
--    (数据口径、注意事项),功能可做什么不写。V25 种的那两条直接删,系统参数那条
--    保留前半句(说明这批键的用途)、去掉"支持新增、编辑与删除"。
-- 2) 宽表横向滚动后用户会读错行(不知道这一行是谁),首列(账号/角色编码/配置键)
--    钉在左侧是最治本的解法;前端 ConfigurableList 与列表配置编辑器已支持 fixed 字段。
--
-- 只用普通 UPDATE + JSON 函数,不含 CTE / 窗口函数 / IF() / 动态 SQL,兼容
-- Flyway 11.7.2 在 MySQL 8.4 上退化的 MySQL-5 解析器。

-- 1) 删掉纯功能罗列的说明(键不存在时 JSON_REMOVE 不报错,重复执行安全)
UPDATE sys_list_config
SET page_json = JSON_REMOVE(page_json, '$.description')
WHERE resource_code IN ('system-users', 'system-roles')
  AND deleted = 0;

-- 系统参数:保留"这批键是干什么的",去掉"支持新增、编辑与删除"
UPDATE sys_list_config
SET page_json = JSON_REPLACE(page_json, '$.description', '维护全局键值参数(token 有效期、密码强度策略等)')
WHERE resource_code = 'system-configs'
  AND deleted = 0;

-- 2) 首列固定:left 会渲染成横向滚动时钉住的列,其余列不动
UPDATE sys_list_config
SET columns_json = JSON_SET(columns_json, '$[0].fixed', 'left')
WHERE resource_code IN ('system-users', 'system-roles', 'system-configs')
  AND deleted = 0;
