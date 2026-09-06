-- 业务字典类型种子:教室类型 / 学季 / 授课方式
-- 表单下拉与列表回显改为字典驱动,后续新增选项由管理员在 /system/dicts 页面维护,无需发版。

INSERT IGNORE INTO sys_dict_type (
    id, type_code, type_name, status, remark, deleted
) VALUES
    (2, 'room_type', '教室类型', 'active', '教室管理-教室类型选项', 0),
    (3, 'term_season', '学季', 'active', '学期管理-学季选项', 0),
    (4, 'teaching_method', '授课方式', 'active', '课程/班型-授课方式选项', 0);

INSERT IGNORE INTO sys_dict_item (
    id, type_id, item_code, item_name, item_value, sort_no, status, remark, deleted
) VALUES
    -- room_type 教室类型
    (10, 2, 'normal', '标准教室', 'normal', 10, 'active', NULL, 0),
    (11, 2, 'piano', '琴房', 'piano', 20, 'active', NULL, 0),
    (12, 2, 'lab', '实验室', 'lab', 30, 'active', NULL, 0),
    (13, 2, 'dance', '舞蹈教室', 'dance', 40, 'active', NULL, 0),
    (14, 2, 'art', '美术教室', 'art', 50, 'active', NULL, 0),
    -- term_season 学季
    (20, 3, 'spring', '春季', 'spring', 10, 'active', NULL, 0),
    (21, 3, 'summer', '夏季', 'summer', 20, 'active', NULL, 0),
    (22, 3, 'autumn', '秋季', 'autumn', 30, 'active', NULL, 0),
    (23, 3, 'winter', '冬季', 'winter', 40, 'active', NULL, 0),
    -- teaching_method 授课方式(细粒度在前,粗粒度在后,可按需在字典页面删减)
    (30, 4, 'offline_small_class', '线下小班', 'offline_small_class', 10, 'active', NULL, 0),
    (31, 4, 'offline_large_class', '线下大班', 'offline_large_class', 20, 'active', NULL, 0),
    (32, 4, 'offline_one_to_one', '线下1对1', 'offline_one_to_one', 30, 'active', NULL, 0),
    (33, 4, 'online_small_class', '线上小班', 'online_small_class', 40, 'active', NULL, 0),
    (34, 4, 'online_large_class', '线上大班', 'online_large_class', 50, 'active', NULL, 0),
    (35, 4, 'online_one_to_one', '线上1对1', 'online_one_to_one', 60, 'active', NULL, 0),
    (36, 4, 'offline', '线下', 'offline', 70, 'active', NULL, 0),
    (37, 4, 'online', '线上', 'online', 80, 'active', NULL, 0),
    (38, 4, 'hybrid', '混合', 'hybrid', 90, 'active', NULL, 0);
