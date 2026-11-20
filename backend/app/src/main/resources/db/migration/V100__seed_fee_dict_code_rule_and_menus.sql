-- V100: 收费台账的字典 / 编号规则 / 菜单与授权
--
-- 三件事一起种：
--   1) 字典 fee_payment_method（type id 15，条目 76~80）：收款方式下拉，前后端与列注释共用一套值；
--   2) 编号规则 fee_item（id 1013）：收费项目编码留空时按 FE-001 取号；
--      收款单号**不登记编号规则**，由 Service 按「SK-日期-当日序号」现拼（收款高频、规则页改格式意义不大，
--      且每多一个资源就要同步改 CodeRuleWiringTest 的 DTO 映射，收益不成比例）。
--   3) 菜单：「收费与财务」目录（143）+ 收费台账页（144）+ 收费项目与价目页（145）
--      + 两枚隐藏写按钮（146 台账维护 / 147 项目价目维护）。
--
-- id 避让（执行前复核过）：
--   sys_menu 143~147 空闲（1~25、101~123、130~142 已用，200 起是小程序端）；
--   sys_dict_type 15 空闲（2~14 已用）；sys_dict_item 76~80 空闲（最大 75）；
--   sys_code_rule 1013 空闲（V91 用 1001~1012）；
--   sys_role_menu 取 1430000 / 1431000 / 1432000 + 角色 ID，只加不乘（角色 id 有 8e18 量级的造数角色）。
--
-- 授权口径（与 V92/V96 的教训对齐）：
--   * 目录 143 授给**所有**在职角色。菜单树按已授权节点拼，子页的父目录没被勾到会被提升成顶层散页；
--     目录本身不带权限码、且没有可见子页时不渲染，所以全量授目录不会多出空入口。
--   * 两个页面节点与两个写按钮**只授平台管理员(1)与机构运营(110)**，不按 education:read 全量授。
--     原因：收费金额是财务敏感数据，教师岗持有 education:read 但不该看学费台账；
--     页面节点仍挂 education:read（接口读权限沿用教务读码，见方案），可见性由 sys_role_menu 控制。
--     将来要给财务岗单独开，在角色管理的「分配菜单」里勾 143/144/145/146/147 即可，不用改代码。
--
-- 兼容自查：普通 DML，一条语句一条路径；无 CTE、无窗口函数、无 JSON_TABLE、无 IF( 函数、无 PREPARE；
-- 不建表不改列，不涉及排序规则漂移；全部 INSERT IGNORE，可整脚本重放。

-- 1) 字典类型与条目
INSERT IGNORE INTO sys_dict_type (
    id, type_code, type_name, status, remark, deleted
) VALUES
    (15, 'fee_payment_method', '收款方式', 'active', '收费台账-收款方式选项', 0);

INSERT IGNORE INTO sys_dict_item (
    id, type_id, item_code, item_name, item_value, sort_no, status, remark, deleted
) VALUES
    (76, 15, 'cash', '现金', 'cash', 10, 'active', NULL, 0),
    (77, 15, 'wechat', '微信', 'wechat', 20, 'active', NULL, 0),
    (78, 15, 'alipay', '支付宝', 'alipay', 30, 'active', NULL, 0),
    (79, 15, 'bank', '银行转账', 'bank', 40, 'active', NULL, 0),
    (80, 15, 'other', '其他', 'other', 50, 'active', NULL, 0);

-- 2) 收费项目编码的默认编号规则（与 EducationCodeRuleContributor 的登记一字不差）
INSERT IGNORE INTO sys_code_rule (
    id, resource_code, enabled, prefix, separator_char, date_pattern, serial_length, remark, deleted
) VALUES
    (1013, 'fee_item', 1, 'FE', '-', '', 3, '默认规则,可在编号规则页调整格式或停用', 0);

-- 3) 「收费与财务」目录：排在「学员与班级」之后、「基础资料」之前
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (143, 'admin', NULL, 'education_fee', '收费与财务', 'catalog', NULL, NULL,
     NULL, 'Money', 35, 1, 'active',
     '按学期维护学员收费台账、登记收款、导出收费出勤表；目录不挂权限码，入口显隐由子页面节点决定', 0);

-- 4) 两个页面：读权限沿用 education:read，可见性只授给管理员角色
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (144, 'admin', 143, 'education_fee_ledger', '收费台账', 'menu', '/fee-ledger', 'education/FeeLedgerView',
     'education:read', 'Tickets', 10, 1, 'active',
     '按学期/班级建账、录收费月数与标准、登记多笔收款、从考勤同步出勤、导出收费出勤表；勾选即授教务查看码', 0),
    (145, 'admin', 143, 'education_fee_items', '收费项目与价目', 'menu', '/fee-items', 'education/FeeItemManagementView',
     'education:read', 'Goods', 20, 1, 'active',
     '维护收费项目(按月/按天)与校区/班级/学期/学员四级价目；勾选即授教务查看码', 0);

-- 5) 两枚隐藏写按钮：一个管台账台账/收款/逐月发生，一个管项目与价目配置
INSERT IGNORE INTO sys_menu (
    id, portal, parent_id, menu_code, menu_name, menu_type, path, component,
    permission_code, icon, sort_no, visible, status, remark, deleted
) VALUES
    (146, 'admin', 144, 'education_fee_write', '收费台账维护', 'button', NULL, NULL,
     'education:fee:write', NULL, 10, 0, 'active',
     '建账/改台账/登记收款/覆盖逐月发生额/覆盖出勤天数共用此码；导出只读不拦', 0),
    (147, 'admin', 145, 'education_fee_config_write', '收费项目价目维护', 'button', NULL, NULL,
     'education:fee:config:write', NULL, 10, 0, 'active',
     '收费项目与价目表的增删改共用此码；改价不影响已建台账的标准快照', 0);

-- 6) 目录 143 授给所有在职角色（缺了它，子页面会被提升成顶层散页）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT 1430000 + r.id, r.id, 143, 0
FROM sys_role r
WHERE r.deleted = 0;

-- 7) 页面与写按钮：平台管理员 + 机构运营
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted) VALUES
    (1431001, 1, 144, 0),
    (1431002, 1, 145, 0),
    (1431003, 1, 146, 0),
    (1431004, 1, 147, 0),
    (1431101, 110, 144, 0),
    (1431102, 110, 145, 0),
    (1431103, 110, 146, 0),
    (1431104, 110, 147, 0);

-- 8) 兜底：现场若已自建「财务」类角色并在 CSV 里写了新写码，把页面与按钮补给它
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1432000 + r.id, r.id, 144, 0
FROM sys_role r
WHERE r.deleted = 0
  AND (r.permission_codes LIKE '%education:fee:write%'
       OR r.permission_codes LIKE '%education:fee:config:write%');

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1433000 + r.id, r.id, 145, 0
FROM sys_role r
WHERE r.deleted = 0
  AND (r.permission_codes LIKE '%education:fee:write%'
       OR r.permission_codes LIKE '%education:fee:config:write%');

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1434000 + r.id, r.id, 146, 0
FROM sys_role r
WHERE r.deleted = 0
  AND r.permission_codes LIKE '%education:fee:write%';

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, deleted)
SELECT DISTINCT 1435000 + r.id, r.id, 147, 0
FROM sys_role r
WHERE r.deleted = 0
  AND r.permission_codes LIKE '%education:fee:config:write%';
