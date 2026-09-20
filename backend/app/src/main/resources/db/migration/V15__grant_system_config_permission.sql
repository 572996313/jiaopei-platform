-- V15: Grant system config permissions to PLATFORM_ADMIN and fix menu order
-- V14 inserted the system_config menu and role-menu grant, but did not add the
-- controller-level permission codes (system:config:read/write) to the role's
-- permission_codes, and the menu sort_no (60) placed it before users.

-- Place 系统参数 between menus (130) and dicts (140)
UPDATE sys_menu
SET sort_no = 135,
    component = 'system/config',
    visible = 1
WHERE menu_code = 'system_config'
  AND deleted = 0;

-- Append controller permission codes for PLATFORM_ADMIN (guarded against duplicates)
UPDATE jp_role
SET permission_codes = CONCAT(permission_codes, ',system:config:read,system:config:write')
WHERE role_code = 'PLATFORM_ADMIN'
  AND deleted = 0
  AND permission_codes IS NOT NULL
  AND permission_codes NOT LIKE '%system:config:read%';
