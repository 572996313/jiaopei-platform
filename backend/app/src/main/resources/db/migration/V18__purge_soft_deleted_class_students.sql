-- 班级学员唯一键 uk_jp_class_student (class_id, student_id) 不含 deleted 列，
-- 逻辑删除的记录会持续占用唯一键，导致学员移出后无法再次入班。
-- 班级学员移除已改为物理删除，这里一次性清理历史软删记录以释放唯一键。
DELETE FROM jp_class_student WHERE deleted = 1;
