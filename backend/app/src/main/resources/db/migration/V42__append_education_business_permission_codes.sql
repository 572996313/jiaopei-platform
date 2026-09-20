-- V42: 教培业务模块的权限码回补（属教培部署专属，第二个项目不带本脚本）
-- 历史上业务码 organization:*/education:* 与底座码混写在同一条 V7 角色种子里，
-- V41 已把底座种子裁成只含 tenant:*/system:*，这里按模块归属把业务码补回来。
-- 今后新增业务模块的权限码，照本脚本的写法由各模块自己的迁移追加，不回填底座种子。
-- 注意：CONCAT 不幂等，若要在本地重放本脚本（删 flyway_schema_history 行）必须连 V41 一起删，
-- 让 V41 先把角色串重置成底座段，否则业务码会被重复追加一遍。

UPDATE sys_role
SET permission_codes = CONCAT(permission_codes, ',organization:read,organization:write,education:read,education:write')
WHERE role_code = 'PLATFORM_ADMIN';
