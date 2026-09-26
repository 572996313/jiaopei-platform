-- V106: 三张配置化列表(用户/角色/系统参数)开启表头排序
--
-- 底座分页接口新增服务端排序(sortField/sortOrder + 各 Service 的列白名单),配置化列表
-- 是否可点表头由 columns_json 的 sortable 键决定。能开的列必须已在后端白名单里:
--   system-users   -> SystemUserServiceImpl.USER_SORTABLE
--   system-roles   -> SystemRoleServiceImpl.ROLE_SORTABLE
--   system-configs -> SystemConfigServiceImpl.CONFIG_SORTABLE
-- 口径:白名单里且在页面上显示的列一律开,不挑"这列有没有排序价值";渲染列(institutionId)、
-- 标签/聚合列(roleCodes/permissionCodes)与未入白名单的列(configValue/valueType/isSystem/
-- description)不开。
--
-- 列按下标定位(columns_json 数组顺序,与 V101 种入的内容一致):
--   system-users   0=username 1=displayName 2=institutionId 3=phone 4=roleCodes
--                  5=status 6=passwordResetRequired 7=lastLoginAt 8=remark
--   system-roles   0=roleCode 1=roleName 2=permissionCodes 3=status 4=remark
--   system-configs 0=configKey 1=displayName 2=configValue 3=valueType 4=configGroup
--                  5=isSystem 6=sortOrder 7=status 8=description
-- 走 JSON_SET 只加 sortable 这一个键,下游在本机改过的其余键原样保留。
--
-- 与教培侧 app/.../db/migration/V70__enable_sortable_on_configured_list_columns.sql
-- 内容一字不差:教培自己的库从它的 V1..V69 长起来、不执行 V1xx,新下游只跑 V1xx、
-- 拿不到教培那条目录,所以两边各写一份。
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
