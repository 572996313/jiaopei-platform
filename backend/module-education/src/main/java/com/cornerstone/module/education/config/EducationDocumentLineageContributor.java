package com.cornerstone.module.education.config;

import com.cornerstone.core.system.lineage.DocumentEdge;
import com.cornerstone.core.system.lineage.DocumentLineageContributor;
import com.cornerstone.core.system.lineage.DocumentNode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 教务模块的业务单据链路登记：排课与执行 → 收费。
 *
 * <p>这里只声明代码里真实发生的关系；{@code confirmed=false} 表示业务口径还没拍板
 * （例如结转字段只有预留列、没有服务读写），页面上会显式标成「待确认」。
 * 关系与字段的详细口径见 {@code docs/业务单据链路.md}。</p>
 */
@Component("educationDocumentLineageContributor")
public class EducationDocumentLineageContributor implements DocumentLineageContributor {

    private static final String SCHEDULE_PLAN = "education.schedule_plan";
    private static final String AUTO_BATCH = "education.auto_batch";
    private static final String LESSON = "education.lesson";
    private static final String ATTENDANCE = "education.attendance";
    private static final String CLASS_ROSTER = "education.class_roster";
    private static final String FEE_ITEM = "education.fee_item";
    private static final String FEE_PRICE = "education.fee_price";
    private static final String FEE_LEDGER = "education.fee_ledger";
    private static final String FEE_LEDGER_ITEM = "education.fee_ledger_item";
    private static final String FEE_ATTENDANCE_MONTH = "education.fee_attendance_month";
    private static final String FEE_MONTHLY = "education.fee_monthly";
    private static final String FEE_PAYMENT = "education.fee_payment";

    @Override
    public String moduleKey() {
        return "education";
    }

    @Override
    public List<DocumentNode> documents() {
        return List.of(
            new DocumentNode(SCHEDULE_PLAN, "周期排课计划", "jp_schedule_plan", List.of(),
                "/scheduling", "education:schedule:write",
                "按每周节次模式与起止日期展开成课次", true, null),
            new DocumentNode(AUTO_BATCH, "自动排课批次", "jp_schedule_auto_batch", List.of(),
                "/scheduling", "education:schedule:write",
                "按完成度缺口补排的执行记录，也是整批撤销的依据", true, null),
            new DocumentNode(LESSON, "课次", "schedule_lesson", List.of("schedule_teacher_rel"),
                "/schedule", "education:attendance:write",
                "排课明细；来源看 plan_id（计划展开）或 auto_batch_id（自动补排）", true, null),
            new DocumentNode(ATTENDANCE, "考勤", "jp_attendance", List.of(),
                "/schedule", "education:attendance:write",
                "学员 × 课次的出勤记录；按天收费只算 present / late", true, null),
            new DocumentNode(CLASS_ROSTER, "班级名册", "jp_class_student", List.of(),
                "/class-roster", "education:class:write",
                "班级学员关系；V109 起即报名记录，status=active 才参与建账", true, null),
            new DocumentNode(FEE_ITEM, "收费项目", "jp_fee_item", List.of(),
                "/fee-items", "education:fee:config:write",
                "收费项目档案；计费方式为按月或按天", true, null),
            new DocumentNode(FEE_PRICE, "收费价目", "jp_fee_price", List.of(),
                "/fee-items", "education:fee:config:write",
                "价目表；按学员 > 班级 > 校区 > 学期 > 全局取最贴合的一行", true, null),
            new DocumentNode(FEE_LEDGER, "收费台账", "jp_fee_ledger", List.of(),
                "/fee-ledger", "education:fee:write",
                "学员收费台账头；一个学员 × 一个班级 × 一个学期一份", true, null),
            new DocumentNode(FEE_LEDGER_ITEM, "应收台账明细", "jp_fee_ledger_item", List.of(),
                "/fee-ledger", "education:fee:write",
                "每学员每收费项目一行；标准、收费月数、上期结余与其他抵扣都在这里", true,
                "结转字段 carry_from_item_id 已预留，当前没有服务读写"),
            new DocumentNode(FEE_ATTENDANCE_MONTH, "月度出勤", "jp_fee_attendance_month", List.of(),
                "/fee-ledger", "education:fee:write",
                "每台账 × 每账期一行；attendanceDays 是生效值，autoDays 是考勤汇总值", true,
                "转班/插班后的出勤归属待业务确认"),
            new DocumentNode(FEE_MONTHLY, "逐月发生额", "jp_fee_monthly", List.of(),
                "/fee-ledger", "education:fee:write",
                "每明细 × 每账期一行；支持手工覆盖，覆盖后不随重算变化", true, null),
            new DocumentNode(FEE_PAYMENT, "收款流水", "jp_fee_payment", List.of(),
                "/fee-ledger", "education:fee:write",
                "一笔收款挂到某条明细；金额允许负数用于冲正", true,
                "按月核销还是按明细核销待财务确认")
        );
    }

    @Override
    public List<DocumentEdge> edges() {
        return List.of(
            // 教务执行
            new DocumentEdge(SCHEDULE_PLAN, LESSON, "展开", List.of("plan_id"),
                "提交排课计划", "POST /api/education/schedule-plans/commit",
                "计划按勾选课次生成；已生成的键幂等跳过", true),
            new DocumentEdge(AUTO_BATCH, LESSON, "生成", List.of("auto_batch_id"),
                "执行自动补排", "POST /api/education/schedule-auto-batches/run",
                "按完成度缺口生成课次并记为一个批次", true),
            new DocumentEdge(LESSON, ATTENDANCE, "执行记录",
                List.of("lesson_id", "class_id", "attendance_date"),
                "保存课次点名", "POST /api/education/attendance/lesson-batch",
                "点名记录学员出勤状态；一天多节课只算一天出勤", true),

            // 收费
            new DocumentEdge(CLASS_ROSTER, FEE_LEDGER, "生成",
                List.of("class_id", "student_id", "term_id"),
                "按名册建账", "POST /api/education/fee-ledgers/generate",
                "为名册内每个在读学员建一份台账，已有台账的跳过", true),
            new DocumentEdge(FEE_LEDGER, FEE_LEDGER_ITEM, "展开", List.of("ledger_id"),
                "建账/编辑台账", "POST /api/education/fee-ledgers",
                "按当前启用的收费项目展开明细，并按价目表取价", true),
            new DocumentEdge(FEE_ITEM, FEE_LEDGER_ITEM, "引用", List.of("fee_item_id"),
                "建账/编辑台账", "POST /api/education/fee-ledgers",
                "明细的收费项目来自收费项目档案", true),
            new DocumentEdge(FEE_PRICE, FEE_LEDGER_ITEM, "取价",
                List.of("fee_item_id", "campus_id", "class_id", "term_id", "student_id"),
                "重新取价并重算", "POST /api/education/fee-ledgers/{id}/resolve-prices",
                "按学员 > 班级 > 校区 > 学期 > 全局取最贴合价目；月标准与日标准各自回落", true),
            new DocumentEdge(ATTENDANCE, FEE_ATTENDANCE_MONTH, "按月汇总",
                List.of("class_id", "billing_month", "student_id"),
                "从考勤汇总", "POST /api/education/fee-attendance/sync",
                "只算 present / late，用 COUNT(DISTINCT attendance_date)", true),
            new DocumentEdge(FEE_ATTENDANCE_MONTH, FEE_MONTHLY, "重算",
                List.of("ledger_id", "billing_month"),
                "重算发生额", "POST /api/education/fee-monthly/recalculate",
                "按月项目取月标准 × 月份系数；按天项目取出勤天数 × 日标准", true),
            new DocumentEdge(FEE_LEDGER_ITEM, FEE_MONTHLY, "生成",
                List.of("ledger_item_id", "billing_month"),
                "建账/重算", "POST /api/education/fee-monthly/recalculate",
                "每条明细在学期内每个账期生成一行；手工覆盖的发生额不随重算变化", true),
            new DocumentEdge(FEE_PAYMENT, FEE_LEDGER_ITEM, "核销",
                List.of("ledger_id", "ledger_item_id", "billing_month"),
                "登记收款", "POST /api/education/fee-payments",
                "收款只引用明细，不回写金额；实收与结余由流水现算", true),
            new DocumentEdge(FEE_LEDGER_ITEM, FEE_LEDGER_ITEM, "结转",
                List.of("carry_from_item_id"), null, null,
                "结转字段已预留，当前没有任何服务读写；是否需要启用待业务确认", false)
        );
    }
}
