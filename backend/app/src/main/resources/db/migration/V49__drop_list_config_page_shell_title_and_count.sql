-- V49: 收掉列表配置页面壳里已无人读取的 title / showCount / countFormat
--
-- 界面口径变了(用户反馈的直接结果):
-- 1) 页头 h1 显示的就是菜单节点名(sys_menu.menuName),配置化列表页再渲染一个
--    "用户列表/角色列表"属于同一件事说两遍,所以前端不再渲染卡片标题;
-- 2) "共 X 条"计数全站去掉:分页表看分页器的总数即可,不分页的表都是刻意选定的
--    小体量表(字典/菜单/校区/教室/学期/时间段/班型/课程分类/弹窗内表格),不关心条数。
-- 两个键既然没人渲染了,配置里就不许再留着——"配了不生效"在本仓库按缺陷处理。
-- 页面壳从此只有 description(怎么生效)与 emptyText(空态)两键。
--
-- 只用普通 UPDATE + JSON 函数,不含 CTE / 窗口函数 / IF() / 动态 SQL,兼容
-- Flyway 11.7.2 在 MySQL 8.4 上退化的 MySQL-5 解析器;键不存在时 JSON_REMOVE 不报错,重放安全。
-- JSON_VALID 守卫是必要的:配置管理页允许手输 page_json,非法 JSON 直接喂给 JSON_REMOVE 会让迁移失败。

UPDATE sys_list_config
SET page_json = JSON_REMOVE(page_json, '$.title', '$.showCount', '$.countFormat')
WHERE page_json IS NOT NULL
  AND JSON_VALID(page_json)
  AND deleted = 0;
