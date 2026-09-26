-- V70: 三张配置化列表(用户/角色/系统参数)开启表头排序
--
-- 用户反馈"一个一个搜太费劲",列表需要支持按列排序。排序是**服务端**的:前端把
-- sortField/sortOrder 传给分页接口,后端按各 Service 的白名单映射成真实列,白名单外的
-- 字段直接 BAD_REQUEST。所以 columns_json 里能开 sortable 的列,必须是后端白名单已收录的
-- 那几个,否则表头能点、请求回来报错。对应关系:
--   system-users   -> SystemUserServiceImpl.USER_SORTABLE
--   system-roles   -> SystemRoleServiceImpl.ROLE_SORTABLE
--   system-configs -> SystemConfigServiceImpl.CONFIG_SORTABLE
--
-- 口径:白名单里的列只要在页面上显示着一律开 sortable,不挑"这列有没有排序价值",
-- 免得留下"为什么这列能点、那列不能点"这种解释不了的差异。
-- 不开的是渲染列与聚合列:institutionId(渲染机构名)、roleCodes/permissionCodes(标签组)、
-- configValue/valueType/isSystem/description(后端未入白名单)。
--
-- 列按下标定位(columns_json 数组顺序,与 V39 整值覆盖后的内容一致,底座 V101 同序):
--   system-users   0=username 1=displayName 2=institutionId 3=phone 4=roleCodes
--                  5=status 6=passwordResetRequired 7=lastLoginAt 8=remark
--   system-roles   0=roleCode 1=roleName 2=permissionCodes 3=status 4=remark
--   system-configs 0=configKey 1=displayName 2=configValue 3=valueType 4=configGroup
--                  5=isSystem 6=sortOrder 7=status 8=description
-- 走 JSON_SET 只加 sortable 这一个键,V48 给首列加的 fixed=left 等其余键原样保留。
--
-- 与底座侧 cornerstone/migration/V106__enable_sortable_on_configured_list_columns.sql
-- 内容一字不差:这三行由底座持有,教培自己的库从 app 的 V1..V69 长起来、不执行 V1xx,
-- 新下游只跑 V1xx、拿不到本目录,所以两边各写一份(见 AGENTS.md 双轨规则)。
--
-- 只用普通 UPDATE + JSON 函数,不含 CTE / 窗口函数 / IF() / 动态 SQL,兼容
-- Flyway 11.7.2 在 MySQL 8.4 上退化的 MySQL-5 解析器。

UPDATE sys_list_config
SET columns_json = JSON_SET(
    columns_json,
    '$[0].sortable', TRUE,
    '$[1].sortable', TRUE,
    '$[3].sortable', TRUE,
    '$[5].sortable', TRUE,
    '$[6].sortable', TRUE,
    '$[7].sortable', TRUE,
    '$[8].sortable', TRUE
)
WHERE resource_code = 'system-users'
  AND deleted = 0;

UPDATE sys_list_config
SET columns_json = JSON_SET(
    columns_json,
    '$[0].sortable', TRUE,
    '$[1].sortable', TRUE,
    '$[3].sortable', TRUE,
    '$[4].sortable', TRUE
)
WHERE resource_code = 'system-roles'
  AND deleted = 0;

UPDATE sys_list_config
SET columns_json = JSON_SET(
    columns_json,
    '$[0].sortable', TRUE,
    '$[1].sortable', TRUE,
    '$[4].sortable', TRUE,
    '$[6].sortable', TRUE,
    '$[7].sortable', TRUE
)
WHERE resource_code = 'system-configs'
  AND deleted = 0;
