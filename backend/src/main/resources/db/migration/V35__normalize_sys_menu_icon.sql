-- V35: 规整 sys_menu.icon 为 Element Plus 标准图标名(PascalCase)
-- 前端图标解析已放开为全量 EP 图标(大小写/连字符不敏感),不再维护 home/users 等语义别名;
-- 本迁移把存量非标准值一次性洗成标准名,此后新增菜单直接配置标准图标名即可。
UPDATE sys_menu SET icon = 'House'        WHERE icon = 'home';
UPDATE sys_menu SET icon = 'UserFilled'   WHERE icon IN ('users', 'team');
UPDATE sys_menu SET icon = 'Select'       WHERE icon = 'checkbox';
UPDATE sys_menu SET icon = 'Money'        WHERE icon = 'wallet';
UPDATE sys_menu SET icon = 'Bell'         WHERE icon = 'notification';
UPDATE sys_menu SET icon = 'Reading'      WHERE icon = 'book';
UPDATE sys_menu SET icon = 'OfficeBuilding' WHERE icon IN ('office', 'office-building');
-- 历史小写/混写值统一为组件名写法(不区分大小写的列比对下这些 UPDATE 幂等,重复执行无副作用)
UPDATE sys_menu SET icon = 'Setting'      WHERE icon = 'setting';
UPDATE sys_menu SET icon = 'User'         WHERE icon = 'user';
UPDATE sys_menu SET icon = 'Menu'         WHERE icon = 'menu';
UPDATE sys_menu SET icon = 'Tools'        WHERE icon = 'tools';
UPDATE sys_menu SET icon = 'List'         WHERE icon = 'list';
UPDATE sys_menu SET icon = 'Lock'         WHERE icon = 'lock';
UPDATE sys_menu SET icon = 'Connection'   WHERE icon = 'connection';
UPDATE sys_menu SET icon = 'Calendar'     WHERE icon = 'calendar';
UPDATE sys_menu SET icon = 'AlarmClock'   WHERE icon = 'alarm-clock';
