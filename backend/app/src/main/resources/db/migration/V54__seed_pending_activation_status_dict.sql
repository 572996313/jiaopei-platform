-- V54：通用状态字典补「待激活」条目
--
-- 背景：pending_activation 由底座 AuthInvitationService.STATUS_PENDING_ACTIVATION 定义（预建账号，
--   凭激活码自设密码后转 active），但 common_status 只有启用/停用两项，用户管理页的状态下拉选不到它，
--   注册凭证页的「待激活账号」下拉恒为空，整条预建-激活链路在界面上走不通。
--   表单与筛选栏都读这一份字典（optionsLoader=status 与 dict-select 同源），补条目即可，不改任何前后端代码。

INSERT IGNORE INTO sys_dict_item (
    id, type_id, item_code, item_name, item_value, sort_no, status, remark, deleted
) VALUES
    (60, 1, 'pending_activation', '待激活', 'pending_activation', 5, 'active', '预建账号,凭激活码自设密码后转启用', 0);
