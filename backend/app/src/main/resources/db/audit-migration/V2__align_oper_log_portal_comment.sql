-- 把 oper_log.portal 的字段注释对齐底座基线（cornerstone/audit-migration/V100）。
-- 原注释写死了 "student=学生,parent=家长" —— 那是教培自己的端，属底座表里的业务专属语义，
-- 第二个消费方（achievement）没有这两个端。基线脚本换成中性说法，本脚本把存量库跟上。
-- 纯注释变更，MODIFY 的列定义与 V1 完全一致，不隐式改列。
ALTER TABLE `oper_log`
    MODIFY COLUMN `portal` varchar(16) NULL COMMENT '操作端口(admin=管理后台,其余取值由业务侧 PortalResolver 决定)';
