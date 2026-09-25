-- V60: 清理小程序角色上无人使用的 miniapp:base 权限码
-- 背景：V27 建 MINIAPP_STUDENT/MINIAPP_PARENT 两个角色时权限码种的是 miniapp:base，
-- 随后 V28 追加了真正生效的 miniapp:access（MiniappController 七个接口只校验它），
-- 但 V27 那条从来没被摘掉，也没有任何 @RequiresPermission、菜单节点或列表按钮引用它。
-- 结果功能权限总览长期把它报成"未登记权限码"，角色权限弹窗里多一条无意义码。
-- 只摘 miniapp:base 这一个 token，角色若被运维加过其它码不受影响。

UPDATE sys_role
SET permission_codes = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', permission_codes, ','), ',miniapp:base,', ','))
WHERE role_code IN ('MINIAPP_STUDENT', 'MINIAPP_PARENT')
  AND deleted = 0
  AND permission_codes LIKE '%miniapp:base%';
