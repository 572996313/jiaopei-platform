-- V53: 教室类型/学季/授课方式 收口到系统字典(V19 欠账)
-- 背景：V19 只种了 room_type/term_season/teaching_method 三组字典并让前端改读字典，
-- 但列注释仍写着"教室类型 normal普通"这类旧说明、后端写入也没按字典校验，
-- 结果 jp_term.season 里落进了中文展示名(如"秋季")。本脚本归一存量并把列注释指向字典。
-- 归一后：字典是唯一取值来源，后端写入经 DictValueValidator 校验，前端下拉/回显读字典。

-- 存量归一：历史表单把字典的中文展示名直接存进了 season 列，换成字典 item_value。
UPDATE jp_term SET season = 'spring' WHERE season = '春季';
UPDATE jp_term SET season = 'summer' WHERE season = '夏季';
UPDATE jp_term SET season = 'autumn' WHERE season = '秋季';
UPDATE jp_term SET season = 'winter' WHERE season = '冬季';
UPDATE jp_term SET season = NULL WHERE season = '';

-- 列注释标注字典出处(列定义原样照抄，MODIFY 漏写会被隐式改列)
ALTER TABLE jp_classroom MODIFY COLUMN room_type VARCHAR(32) NOT NULL DEFAULT 'normal' COMMENT '教室类型字典(room_type:normal=标准教室,piano=琴房,lab=实验室,dance=舞蹈教室,art=美术教室)';
ALTER TABLE jp_term MODIFY COLUMN season VARCHAR(32) NULL COMMENT '学季字典(term_season:spring=春季,summer=夏季,autumn=秋季,winter=冬季)';
ALTER TABLE jp_course MODIFY COLUMN teaching_method VARCHAR(32) NULL COMMENT '授课方式字典(teaching_method:offline_small_class=线下小班,offline_large_class=线下大班,offline_one_to_one=线下1对1,online_small_class=线上小班,online_large_class=线上大班,online_one_to_one=线上1对1,offline=线下,online=线上,hybrid=混合)';
ALTER TABLE jp_class_type MODIFY COLUMN teaching_method VARCHAR(32) NULL COMMENT '授课方式字典(teaching_method:offline_small_class=线下小班,offline_large_class=线下大班,offline_one_to_one=线下1对1,online_small_class=线上小班,online_large_class=线上大班,online_one_to_one=线上1对1,offline=线下,online=线上,hybrid=混合)';
