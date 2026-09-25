-- V66：公告中心进菜单表（页面节点带读取码 + 隐藏的写按钮）
-- 按 AGENTS「权限码与菜单节点的分配规则」：读取码挂 menu、写码挂 visible=0 的 button、catalog 不挂码。
-- announcement:read / announcement:write 两个码是公告专用的粗码，理由与 education:read|write 同构：
-- 勾页面=入口可见+能读，不留"看得见却读不了"的 403 空洞；勾按钮才给发布/撤回/删除。
-- 注意预设权限 CSV 与已勾菜单节点取并集（UserAccessTokenService），撤销授权时两边都要撤。

INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (25, 'admin', NULL, 'announcement_center', '公告中心', 'menu', '/announcements', 'education/AnnouncementCenterView',
     'announcement:read', 'Bell', 95, 1, 'active', '公告中心页；勾选即授公告查看码', 0),
    (119, 'admin', 25, 'announcement_write', '公告维护', 'button', NULL, NULL,
     'announcement:write', NULL, 10, 0, 'active',
     '新建/编辑/发布/撤回/删除公告共用这一个码，勾了即可写', 0);

-- 平台管理员补授权，否则菜单树里勾不到（沿用 V61 的 6000+ 号段，本批用 6200+）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (6201, 1, 25, 0),
    (6202, 1, 119, 0);
