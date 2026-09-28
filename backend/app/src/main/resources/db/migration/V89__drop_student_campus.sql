-- V89：学员档案不再持有校区
--
-- 校区回答的是"这个学员这一段课在哪个校区上"，它的事实源是 jp_class.campus_id：
-- 报名（jp_enrollment）与在班（jp_class_student）都挂在班级上，班级带校区，链路已经完整。
-- 档案上再放一个单值 campus_id 有两个害处：
--   1）一个学员可以报多个班、班可以分属不同校区，单值列把 1:N 压成 1:1，跨校区就读表达不了；
--   2）改档案列不会回写历史班级，"当时在哪个校区上的课"变得不可追溯。
-- 因此删列，校区一律由班级推导。原先依赖它的两处校验（报名、入班的"学员校区与班级校区不一致"）同批解除。
--
-- 存量不动：3583 条未删除学员里 3580 条有报名或在班记录，其校区随时可由班级反查；
-- 剩下 3 条是小程序自助建档产生的，本来就是 NULL（自助建档一直就没写这个列，功能全程正常）。
--
-- 顺序按"先断外键、再断其支撑索引、最后删列"，避免 1553（Cannot remove index）。
ALTER TABLE `jp_student` DROP FOREIGN KEY `fk_jp_student_campus`;
ALTER TABLE `jp_student` DROP INDEX `fk_jp_student_campus`;
ALTER TABLE `jp_student` DROP COLUMN `campus_id`;
