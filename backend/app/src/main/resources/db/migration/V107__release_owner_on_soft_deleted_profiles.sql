-- V107: 清掉历史墓碑上的档案归属账号，与 softDeleteAndUnlink 口径对齐。
--
-- 起因：本次之前 jp_student 的删除只置 deleted = 1，user_id 原样留在墓碑上；uk_jp_student_user_id
-- 是全表唯一键且不含 deleted，账号因此被一条看不见的档案永久占住，再建档案/再绑定必撞 1062。
-- jp_teacher 虽然从 V56 起删除时就解绑，但更早的墓碑也可能残留 user_id。
--
-- 处置：把已删除行的 user_id 统一置空。唯一键不参与 NULL 比对，账号随即释放；
-- 只动 deleted = 1 的行，活档案不受影响（唯一键也保证活行与墓碑不会同号），迁移幂等。

UPDATE `jp_student` SET `user_id` = NULL WHERE `deleted` = 1 AND `user_id` IS NOT NULL;

UPDATE `jp_teacher` SET `user_id` = NULL WHERE `deleted` = 1 AND `user_id` IS NOT NULL;
