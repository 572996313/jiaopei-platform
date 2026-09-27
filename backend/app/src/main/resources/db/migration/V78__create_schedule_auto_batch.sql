-- 自动排课批次（一期：按缺口补排）
--
-- 这一期只解决"这个班这周还差 3 节，排到哪去"：拿完成度报表算出的缺口（已精确到 日期×星期×节次），
-- 在窗口内的空位里挑一格补上，**不改 jp_schedule_plan**。从空白到整学期课表的纯求解器不在本批——
-- 那要先有教师池与教室池打分，而现在每班只有一个主讲、教室是计划里写死的，硬上只能靠猜。
--
-- 为什么归属记在课次而不是计划：补排的课次可能落在既有计划的格子上（缺口），也可能落在计划之外的
-- 空位（目标比计划格子多出来的那部分）。只有计划上有归属，后一类就找不到主人，撤销也撤不干净。
-- 所以按"谁排的这节课"记在 schedule_lesson.auto_batch_id；AGENTS 里原先写的"计划归属放
-- jp_schedule_plan.auto_batch_id"是给将来的建计划模式留的，那一批再补列，本批不建没有写入方的列。
--
-- auto_batch_id 不建外键，与 V57 的 plan_id 同口径：批次是执行线索，撤销批次改的是批次自己的状态
-- 和课次的有无，不让外键反过来绑住审计记录。
--
-- 批次只撤销、不删除：撤销把 status 改成 rolled_back 并记下时间，本批生成的课次按 auto_batch_id 删掉。
-- 留清单是为了事后能回答"这批课是谁在什么时候自动排的"，所以不给它开删除入口。
--
-- 教师负载上限在这里从"提示"升格成"引擎硬约束"：手工排课时超课时只提示不拦（见 AGENTS 的提示与冲突
-- 分层），因为屏幕前有人在判断；引擎排出来的课没人逐条看，越线就是错，所以选格子时直接不选。
-- 停排日仍是兜底档——有非停排日的位置就不碰它，实在没位置才用，并在批次明细里记一句。

CREATE TABLE jp_schedule_auto_batch (
    id BIGINT NOT NULL COMMENT '主键ID(雪花)',
    institution_id BIGINT NOT NULL COMMENT '机构ID(多租户隔离)',
    campus_id BIGINT NOT NULL COMMENT '校区ID(批次按校区发起,与排课管理页的范围口径一致)',
    term_id BIGINT NOT NULL COMMENT '学期ID(本批发起时所选学期,只作报表筛选范围;课次学期取班级自己的学期,班级没有学期时才回落到它)',
    start_date DATE NOT NULL COMMENT '补排窗口起始日期(不早于今天)',
    end_date DATE NOT NULL COMMENT '补排窗口截止日期',
    class_ids_json VARCHAR(2048) NULL COMMENT '本次纳入的班级ID列表(JSON数组,空=该校区全部待补班级)',
    strategy_json VARCHAR(512) NOT NULL COMMENT '本批实际生效的择优策略(JSON{concentrateClassDays,preferAdjacentSlot,maxDailyLessonsPerClass})',
    requested_count INT NOT NULL DEFAULT 0 COMMENT '待补节数(进入引擎的缺口合计,不小于0;整班退回的班级不在这里)',
    generated_count INT NOT NULL DEFAULT 0 COMMENT '本批生成的课次数',
    unscheduled_count INT NOT NULL DEFAULT 0 COMMENT '没补上的节数(待补-已生成;一条原因可能对应多节,原因看 failure_json)',
    failure_json TEXT NULL COMMENT '没补上的明细(JSON数组[{classId,className,weekNo,reason}],只记未排上的;已排上的直接看课次)',
    status VARCHAR(16) NOT NULL DEFAULT 'completed' COMMENT '批次状态(completed=已执行,rolled_back=已撤销;撤销只删本批未开课的课次)',
    rolled_back_at DATETIME NULL COMMENT '撤销时间(未撤销为空)',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
    PRIMARY KEY (id),
    KEY idx_jp_schedule_auto_batch_scope (institution_id, campus_id, start_date, deleted),
    CONSTRAINT fk_jp_schedule_auto_batch_institution FOREIGN KEY (institution_id) REFERENCES sys_institution (id),
    CONSTRAINT fk_jp_schedule_auto_batch_campus FOREIGN KEY (campus_id) REFERENCES jp_campus (id),
    CONSTRAINT fk_jp_schedule_auto_batch_term FOREIGN KEY (term_id) REFERENCES jp_term (id),
    CONSTRAINT chk_jp_schedule_auto_batch_window CHECK (end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自动排课批次(按缺口补排的执行记录与整批撤销依据)';

ALTER TABLE schedule_lesson
    ADD COLUMN auto_batch_id BIGINT NULL COMMENT '来源自动排课批次ID(手工排课与计划展开为空,整批撤销按它删)' AFTER plan_id,
    ADD KEY idx_schedule_lesson_auto_batch (auto_batch_id, lesson_date);
