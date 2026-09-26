-- 修正 jp_class 容量校验 CHECK 约束与业务语义不一致的问题:
-- 业务端将 capacity_limit = 0 视为"不限容量"(入班校验仅在 capacity > 0 时生效),
-- 旧约束却把 0 当作"上限 0 人",导致容量 0 的班级入班时人数重算直接违反约束报 500。
-- 统一语义:capacity_limit = 0 表示不限容量;capacity_limit > 0 时当前人数不得超限。
ALTER TABLE jp_class
    DROP CHECK chk_jp_class_students,
    ADD CONSTRAINT chk_jp_class_students CHECK (current_students >= 0 AND (capacity_limit = 0 OR current_students <= capacity_limit));
