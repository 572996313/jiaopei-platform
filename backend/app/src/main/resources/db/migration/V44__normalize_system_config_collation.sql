-- V44: 把 sys_system_config 的排序规则归一到 utf8mb4_unicode_ci
-- 根因：V14 建表只写了 DEFAULT CHARSET=utf8mb4、没写 COLLATE，排序规则就随服务器默认走。
-- 本库与生产的 MySQL 8.4 服务器默认是 utf8mb4_0900_ai_ci，所以这张表建成了 0900，
-- 而其余 10 张底座表都是 unicode_ci —— 混用会在跨表比较时撞 Illegal mix of collations，
-- 下游复制基线脚本时还得手写 COLLATE 才能对齐（achievement 的抄本已经这么干过）。
-- 全新库回放同样收敛：V14 建成 0900 -> 本脚本改回 unicode_ci，最终形态与存量库一致。
ALTER TABLE `sys_system_config` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
