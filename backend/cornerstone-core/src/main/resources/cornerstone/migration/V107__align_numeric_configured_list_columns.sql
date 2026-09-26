-- V107: 配置化列表的量值列右对齐(系统参数的「排序」列)
--
-- 列表口径:表头固定居中,数据格按语义对齐——人数、时长、容量、排序号、金额这类
-- 要比较大小/求和的量值右对齐(位数对齐才看得出量级),id、手机号、编码这类标识符保持左对齐
-- (要抄下来拨出去的字符串右对齐反而难核对)。列渲染由 ConfigurableList 读 columns_json 的
-- align 键,所以配置化列表要右对齐必须落到数据里,不能只改前端默认。
--
-- 三张配置化列表里只有 system-configs 的 sortOrder(第 7 列,下标 6)是量值列:
--   system-users  0=username 1=displayName 2=institutionId 3=phone 4=roleCodes
--                 5=status 6=passwordResetRequired 7=lastLoginAt 8=remark
--                 —— 全是文本/渲染列/日期,phone 是标识符不对齐。
--   system-roles  0=roleCode 1=roleName 2=permissionCodes 3=status 4=remark —— 无量值列。
--   system-configs 0=configKey 1=displayName 2=configValue 3=valueType 4=configGroup
--                  5=isSystem 6=sortOrder 7=status 8=description
--
-- 走 JSON_SET 只加 align 这一个键,下游在本机改过的 fixed/sortable 等其余键原样保留。
--
-- 与教培侧 app/.../db/migration/V71__align_numeric_configured_list_columns.sql
-- 内容一字不差:教培自己的库从它的 V1..V70 长起来、不执行 V1xx,新下游只跑 V1xx、
-- 拿不到教培那条目录,所以两边各写一份。
--
-- 只用普通 UPDATE + JSON 函数,不含 CTE / 窗口函数 / IF() / 动态 SQL,兼容
-- Flyway 11.7.2 在 MySQL 8.4 上退化的 MySQL-5 解析器。

UPDATE sys_list_config
SET columns_json = JSON_SET(columns_json, '$[6].align', 'right')
WHERE resource_code = 'system-configs'
  AND deleted = 0;
