package com.cornerstone.module.education.config;

import com.cornerstone.core.system.schema.SchemaMaintenanceContributor;
import com.cornerstone.core.system.schema.SchemaMaintenanceEntry;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 教务模块各表的维护入口登记：数据字典页据此显示"这批数据去哪个页面改"。
 *
 * <p>只登记有管理页面的表；纯关系表指向承载它的页面（课次的教师关系在课表与点名页、
 * 名册关系在分班与名册页），不另外造入口。随模块开关一起注册/消失。</p>
 */
@Component("educationSchemaMaintenanceContributor")
public class EducationSchemaMaintenanceContributor implements SchemaMaintenanceContributor {

    @Override
    public String moduleKey() {
        return "education";
    }

    @Override
    public List<SchemaMaintenanceEntry> entries() {
        return List.of(
            // 档案
            new SchemaMaintenanceEntry("jp_teacher", "教师管理", "/teachers",
                "教师档案、周/日课时上限与账号绑定"),
            new SchemaMaintenanceEntry("jp_student", "学员管理", "/students",
                "学员档案；不持有校区，按班级归属看校区"),
            new SchemaMaintenanceEntry("jp_student_parent", "学员管理", "/students",
                "家长联系人抽屉里维护"),
            new SchemaMaintenanceEntry("jp_user_student_rel", "学员管理", "/students",
                "登录账号与学员档案的绑定关系"),
            new SchemaMaintenanceEntry("jp_wechat_binding", "学员管理", "/students",
                "微信 openid 与账号的绑定关系"),
            new SchemaMaintenanceEntry("jp_course", "课程管理", "/courses", "课程档案"),
            new SchemaMaintenanceEntry("jp_course_category", "课程管理", "/courses",
                "课程分类，在课程页维护"),
            new SchemaMaintenanceEntry("jp_class_type", "班型管理", "/class-types", "班型档案"),

            // 班级与名册
            new SchemaMaintenanceEntry("jp_class", "班级管理", "/classes",
                "班级主档：课时目标、容量、主讲教师"),
            new SchemaMaintenanceEntry("jp_class_teacher", "班级管理", "/classes",
                "班级任课教师关系"),
            new SchemaMaintenanceEntry("jp_class_opening_rule", "班级管理", "/classes",
                "开班规则，在班级页维护"),
            new SchemaMaintenanceEntry("jp_class_change_notice", "班级管理", "/classes",
                "班级变动提示，随班级/教师变更自动生成"),
            new SchemaMaintenanceEntry("jp_class_student", "分班与名册", "/class-roster",
                "入班/退班关系（V109 起即报名记录）"),

            // 排课与执行
            new SchemaMaintenanceEntry("jp_schedule_plan", "排课管理", "/scheduling",
                "周期排课计划"),
            new SchemaMaintenanceEntry("jp_schedule_rule", "排课管理", "/scheduling",
                "禁排/必排规则"),
            new SchemaMaintenanceEntry("jp_school_calendar_day", "排课管理", "/scheduling",
                "校区停排日（法定假/检修）"),
            new SchemaMaintenanceEntry("jp_schedule_auto_batch", "排课管理", "/scheduling",
                "按缺口补排的批次记录"),
            new SchemaMaintenanceEntry("schedule_lesson", "课表与点名", "/schedule",
                "课次与课次状态"),
            new SchemaMaintenanceEntry("schedule_teacher_rel", "课表与点名", "/schedule",
                "课次的授课教师关系"),
            new SchemaMaintenanceEntry("jp_attendance", "课表与点名", "/schedule",
                "课次点名记录"),

            // 公告
            new SchemaMaintenanceEntry("jp_msg_announcement", "公告中心", "/announcements",
                "公告正文与发布状态"),
            new SchemaMaintenanceEntry("jp_msg_announcement_receiver", "公告中心", "/announcements",
                "公告接收人与已读留痕"),
            new SchemaMaintenanceEntry("jp_msg_announcement_template", "公告中心", "/announcements",
                "公告模板"),

            // 收费
            new SchemaMaintenanceEntry("jp_fee_item", "收费项目", "/fee-items",
                "收费项目（按月/按天计费）"),
            new SchemaMaintenanceEntry("jp_fee_price", "收费项目", "/fee-items",
                "价目表的四个维度（校区/班级/学期/学员，0=不限）"),
            new SchemaMaintenanceEntry("jp_fee_ledger", "收费台账", "/fee-ledger",
                "学员应收台账主表"),
            new SchemaMaintenanceEntry("jp_fee_ledger_item", "收费台账", "/fee-ledger",
                "台账明细（整表覆盖式更新）"),
            new SchemaMaintenanceEntry("jp_fee_payment", "收费台账", "/fee-ledger",
                "收款流水"),
            new SchemaMaintenanceEntry("jp_fee_monthly", "收费台账", "/fee-ledger",
                "逐月发生额（可手工覆盖）"),
            new SchemaMaintenanceEntry("jp_fee_attendance_month", "收费台账", "/fee-ledger",
                "月度出勤天数（按天计费的来源）")
        );
    }
}
