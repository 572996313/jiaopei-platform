-- =============================================================================
-- 底座基线 102：把角色管理表单里的提示语中性化
-- -----------------------------------------------------------------------------
-- 归属：cornerstone-core（V100~V199 号段），只动底座自己种下的 sys_list_config 行。
--
-- 为什么不直接改 V101：V101 已经在 achievement 库跑过，改一个字就会换 Flyway checksum，
-- 下次启动直接 `Migration checksum mismatch`。数据修正一律新增迁移，本仓库的规矩。
--
-- 本脚本刻意不出现被替换掉的原词（用 JSON 路径定位而不是文本 REPLACE），这样
-- ExternalIdVocabularyGuardTest 能对整个 cornerstone 目录做"不许带教培名词"的静态扫描。
-- fields[1] 是 roleName 字段（V101 种子里的顺序：roleCode / roleName / status /
-- permissionCodes / customPermissionCodes / remark）。
--
-- 教培库不走这条路径（它没启用 classpath:cornerstone/migration），本脚本对它无影响；
-- 教培自己那条 system-roles 配置里写"校区管理员"是对的，那个项目本就是教培。
-- =============================================================================

UPDATE `sys_list_config`
   SET `form_json` = JSON_SET(`form_json`, '$.fields[1].placeholder', '如 应用管理员'),
       `updated_at` = NOW()
 WHERE `resource_code` = 'system-roles';

UPDATE `sys_list_config`
   SET `form_json` = JSON_SET(`form_json`, '$.fields[0].placeholder', '如 APP_ADMIN'),
       `updated_at` = NOW()
 WHERE `resource_code` = 'system-roles';
