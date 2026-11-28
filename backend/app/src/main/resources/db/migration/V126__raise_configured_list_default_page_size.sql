-- V126：配置化列表首屏默认每页 20 条（原 10 条）
--
-- 与底座 cornerstone/migration 的 V125 内容一字不差，两边都要写：
-- 教培这条库的 sys_list_config 行是本项目 V20 种的（app 只挂 classpath:db/migration，
-- 底座 V100~V199 这批基线脚本不在本项目的执行清单里），只加 V125 的话教培页面仍是 10 条；
-- 反过来新下游项目跑的是底座基线，只有 V125 才拿得到 20 条。
--
-- 为什么改：主表由 TableFillBox 拉满视口，1440×900 一屏能放 19 行左右，
-- 每页 10 条时下半屏常年空着，现场观感是"表格铺不满、中间有空隙"。
-- 只改仍停在旧默认值 10 的行：现场手工调过（20/30/50）的配置保持不动，重复执行结果一致。

UPDATE `sys_list_config`
SET `page_size` = 20
WHERE `resource_code` IN ('system-users', 'system-roles', 'system-configs')
  AND `page_size` = 10
  AND `deleted` = 0;
