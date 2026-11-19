package com.cornerstone.module.education.config;

import com.cornerstone.core.system.action.ActionCatalogContributor;
import com.cornerstone.core.system.action.ActionDefinition;
import com.cornerstone.core.system.action.ActionMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 教务模块（教师/学员/班级/报名/排课/考勤/公告）的按钮授权项登记。
 *
 * <p>现阶段写操作仍共用六枚页面域权限码，登记模式为 {@link ActionMode#LINKED}：
 * 客户能看到每个按钮，但同码按钮在界面上联动。待 Phase 1 起按风险边界拆码后，
 * 被拆出的动作改为 {@link ActionMode#STANDALONE} 并指向各自的新按钮节点。</p>
 *
 * <p>纯前端动作（统计、查看考勤/报名等跳转）登记为 {@link ActionMode#UI_ONLY}，
 * 不建节点、不造假码，跟随所属页面的读取权限。</p>
 */
@Component("educationActionCatalogContributor")
public class EducationActionCatalogContributor implements ActionCatalogContributor {

    private static final String PAGE_TEACHERS = "education_teachers";
    private static final String PAGE_STUDENTS = "education_students";
    private static final String PAGE_ENROLLMENTS = "education_enrollments";
    private static final String PAGE_CLASSES = "education_classes";
    private static final String PAGE_ROSTER = "education_class_roster";
    private static final String PAGE_COURSES = "education_courses";
    private static final String PAGE_CLASS_TYPES = "education_class_types";
    private static final String PAGE_SCHEDULING = "education_scheduling";
    private static final String PAGE_COMPLETENESS = "education_completeness";
    private static final String PAGE_EXECUTION = "education_execution";
    private static final String PAGE_ATTENDANCE_OVERVIEW = "education_attendance_overview";
    private static final String PAGE_ANNOUNCEMENTS = "announcement_center";

    private static final String GRANT_PROFILE = "education_profile_write";
    private static final String GRANT_CLASS = "education_class_write";
    private static final String GRANT_CATALOG = "education_catalog_write";
    private static final String GRANT_SCHEDULE = "education_scheduling_write";
    private static final String GRANT_ATTENDANCE = "education_execution_write";
    private static final String GRANT_ATTENDANCE_MANAGE = "education_attendance_manage";
    private static final String GRANT_ANNOUNCEMENT = "announcement_write";

    private static final String PROFILE_WRITE = "education:profile:write";
    private static final String CLASS_WRITE = "education:class:write";
    private static final String CATALOG_WRITE = "education:catalog:write";
    private static final String SCHEDULE_WRITE = "education:schedule:write";
    private static final String ATTENDANCE_WRITE = "education:attendance:write";
    private static final String ATTENDANCE_MANAGE = "education:attendance:manage";
    private static final String ANNOUNCEMENT_WRITE = "announcement:write";

    @Override
    public String moduleKey() {
        return "education";
    }

    @Override
    public List<ActionDefinition> actions() {
        return List.of(
            // 教师档案
            linked(PAGE_TEACHERS, "teacher-create", GRANT_PROFILE, PROFILE_WRITE,
                "新增教师", "新建教师档案", 10),
            linked(PAGE_TEACHERS, "teacher-edit", GRANT_PROFILE, PROFILE_WRITE,
                "编辑教师", "修改教师资料与带班上限", 20),
            linked(PAGE_TEACHERS, "teacher-delete", GRANT_PROFILE, PROFILE_WRITE,
                "删除教师", "删除未被课次引用的教师档案", 30),
            sensitive(PAGE_TEACHERS, "teacher-import", GRANT_PROFILE, PROFILE_WRITE,
                "导入教师档案", "按模板批量新建教师", 40),
            linked(PAGE_TEACHERS, "teacher-export", GRANT_PROFILE, PROFILE_WRITE,
                "导出教师档案", "导出当前筛选范围内的教师资料", 50),
            sensitive(PAGE_TEACHERS, "teacher-account-link", GRANT_PROFILE, PROFILE_WRITE,
                "匹配登录账号", "把教师档案绑定到登录账号", 60),
            uiOnly(PAGE_TEACHERS, "teacher-stats", "查看统计",
                "展开本页统计条，不额外访问数据", 70),

            // 学员档案
            linked(PAGE_STUDENTS, "student-create", GRANT_PROFILE, PROFILE_WRITE,
                "新增学员", "新建学员档案", 10),
            linked(PAGE_STUDENTS, "student-edit", GRANT_PROFILE, PROFILE_WRITE,
                "编辑学员", "修改学员资料", 20),
            linked(PAGE_STUDENTS, "student-delete", GRANT_PROFILE, PROFILE_WRITE,
                "删除学员", "删除未产生报名记录的学员", 30),
            linked(PAGE_STUDENTS, "student-parent-write", GRANT_PROFILE, PROFILE_WRITE,
                "维护家长联系人", "新增、编辑、停用家长联系人", 40),
            uiOnly(PAGE_STUDENTS, "student-attendance-view", "查看考勤",
                "打开该学员的考勤明细抽屉，只读", 50),
            uiOnly(PAGE_STUDENTS, "student-enrollment-view", "查看报名",
                "查看该学员的报名记录，只读", 60),

            // 学员报名
            linked(PAGE_ENROLLMENTS, "enrollment-create", GRANT_CLASS, CLASS_WRITE,
                "新增报名", "报名后同事务自动写入班级名册", 10),

            // 班级档案
            linked(PAGE_CLASSES, "class-create", GRANT_CLASS, CLASS_WRITE,
                "新增班级", "新建班级主档", 10),
            linked(PAGE_CLASSES, "class-edit", GRANT_CLASS, CLASS_WRITE,
                "编辑班级", "修改班级资料、课时目标与容量", 20),
            linked(PAGE_CLASSES, "class-delete", GRANT_CLASS, CLASS_WRITE,
                "删除班级", "删除未产生课次的班级", 30),
            linked(PAGE_CLASSES, "class-teacher-create", GRANT_CLASS, CLASS_WRITE,
                "新增任课教师", "为班级添加任课教师关系", 40),
            linked(PAGE_CLASSES, "class-teacher-edit", GRANT_CLASS, CLASS_WRITE,
                "编辑任课教师", "修改任课教师的角色与有效期", 50),
            linked(PAGE_CLASSES, "class-teacher-delete", GRANT_CLASS, CLASS_WRITE,
                "删除任课教师", "删除任课关系；主讲删除后自动补位", 60),
            linked(PAGE_CLASSES, "class-primary-teacher-set", GRANT_CLASS, CLASS_WRITE,
                "设为主讲", "把某位任课教师设为主讲", 70),

            // 分班与名册
            linked(PAGE_ROSTER, "roster-create", GRANT_CLASS, CLASS_WRITE,
                "新增入班", "把学员直接补录入班，不回写报名台账", 10),
            linked(PAGE_ROSTER, "roster-remove", GRANT_CLASS, CLASS_WRITE,
                "移出班级", "把学员移出班级名册", 20),

            // 课程档案
            linked(PAGE_COURSES, "course-create", GRANT_CATALOG, CATALOG_WRITE,
                "新增课程", "新建课程档案", 10),
            linked(PAGE_COURSES, "course-edit", GRANT_CATALOG, CATALOG_WRITE,
                "编辑课程", "修改课程资料", 20),
            linked(PAGE_COURSES, "course-delete", GRANT_CATALOG, CATALOG_WRITE,
                "删除课程", "删除未被班级引用的课程", 30),
            linked(PAGE_COURSES, "course-category-create", GRANT_CATALOG, CATALOG_WRITE,
                "新增课程分类", "新建课程分类", 40),
            linked(PAGE_COURSES, "course-category-edit", GRANT_CATALOG, CATALOG_WRITE,
                "编辑课程分类", "修改分类名称与状态", 50),
            linked(PAGE_COURSES, "course-category-delete", GRANT_CATALOG, CATALOG_WRITE,
                "删除课程分类", "删除未被课程引用的分类", 60),

            // 班型档案
            linked(PAGE_CLASS_TYPES, "class-type-create", GRANT_CATALOG, CATALOG_WRITE,
                "新增班型", "新建班型档案", 10),
            linked(PAGE_CLASS_TYPES, "class-type-edit", GRANT_CATALOG, CATALOG_WRITE,
                "编辑班型", "修改班型名称与编码", 20),
            linked(PAGE_CLASS_TYPES, "class-type-delete", GRANT_CATALOG, CATALOG_WRITE,
                "删除班型", "删除未被班级引用的班型", 30),

            // 排课管理
            linked(PAGE_SCHEDULING, "schedule-plan-create", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "新建周期计划", "为班级建立周期性排课计划", 10),
            linked(PAGE_SCHEDULING, "schedule-plan-edit", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "编辑周期计划", "修改计划周期、节次与教师", 20),
            linked(PAGE_SCHEDULING, "schedule-plan-delete", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "删除周期计划", "删除未生成课次的计划", 30),
            linked(PAGE_SCHEDULING, "schedule-rule-create", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "新增排课规则", "新增禁排或限制规则", 40),
            linked(PAGE_SCHEDULING, "schedule-rule-edit", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "编辑排课规则", "修改规则条件与生效范围", 50),
            linked(PAGE_SCHEDULING, "schedule-rule-delete", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "删除排课规则", "删除后该限制立即失效", 60),
            linked(PAGE_SCHEDULING, "schedule-calendar-edit", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "维护校区停排日", "新增、编辑、删除校区日历停排日", 70),
            linked(PAGE_SCHEDULING, "schedule-lesson-create", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "新增课次", "手工新增单节课次", 80),
            linked(PAGE_SCHEDULING, "schedule-lesson-edit", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "编辑课次", "调整课次时间、教室与教师", 90),
            linked(PAGE_SCHEDULING, "schedule-lesson-delete", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "删除课次", "删除尚未上课的课次", 100),

            // 排课完成度
            sensitive(PAGE_COMPLETENESS, "schedule-auto-fill", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "一键补排", "按完成度缺口批量生成未来课次", 10),

            // 执行与课表
            linked(PAGE_EXECUTION, "attendance-save", GRANT_ATTENDANCE, ATTENDANCE_WRITE,
                "保存点名", "保存本人授课课次的出勤状态", 10),
            sensitive(PAGE_EXECUTION, "attendance-manage", GRANT_ATTENDANCE_MANAGE, ATTENDANCE_MANAGE,
                "代点任意课次", "不受授课教师限制地点名与补录考勤", 20),
            linked(PAGE_EXECUTION, "lesson-complete", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "结课", "把课次标记为已上课", 30),
            linked(PAGE_EXECUTION, "lesson-reschedule", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "调课", "调整课次时间或教师", 40),
            linked(PAGE_EXECUTION, "lesson-cancel", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "停课", "停掉某节课次", 50),
            linked(PAGE_EXECUTION, "lesson-makeup", GRANT_SCHEDULE, SCHEDULE_WRITE,
                "补课", "为停掉的课次安排补课", 60),

            // 点名总览
            linked(PAGE_ATTENDANCE_OVERVIEW, "attendance-overview-mark", GRANT_ATTENDANCE,
                ATTENDANCE_WRITE, "点名", "从点名总览进入课次点名", 10),

            // 公告中心
            linked(PAGE_ANNOUNCEMENTS, "announcement-create", GRANT_ANNOUNCEMENT, ANNOUNCEMENT_WRITE,
                "新建公告", "起草公告并按范围试算接收人", 10),
            linked(PAGE_ANNOUNCEMENTS, "announcement-edit", GRANT_ANNOUNCEMENT, ANNOUNCEMENT_WRITE,
                "编辑公告", "修改草稿或已撤回公告", 20),
            linked(PAGE_ANNOUNCEMENTS, "announcement-delete", GRANT_ANNOUNCEMENT, ANNOUNCEMENT_WRITE,
                "删除公告", "删除公告及其接收记录", 30),
            sensitive(PAGE_ANNOUNCEMENTS, "announcement-publish", GRANT_ANNOUNCEMENT, ANNOUNCEMENT_WRITE,
                "发布公告", "立即推送给范围内的接收人", 40),
            sensitive(PAGE_ANNOUNCEMENTS, "announcement-recall", GRANT_ANNOUNCEMENT, ANNOUNCEMENT_WRITE,
                "撤回公告", "撤回已发布公告，接收端不再展示", 50),
            linked(PAGE_ANNOUNCEMENTS, "announcement-template-create", GRANT_ANNOUNCEMENT, ANNOUNCEMENT_WRITE,
                "新增公告模板", "新建可复用的公告模板", 60),
            linked(PAGE_ANNOUNCEMENTS, "announcement-template-edit", GRANT_ANNOUNCEMENT, ANNOUNCEMENT_WRITE,
                "编辑公告模板", "修改模板标题与正文", 70),
            linked(PAGE_ANNOUNCEMENTS, "announcement-template-delete", GRANT_ANNOUNCEMENT, ANNOUNCEMENT_WRITE,
                "删除公告模板", "删除未被引用的模板", 80)
        );
    }

    private ActionDefinition linked(String pageMenuCode, String actionKey, String grantMenuCode,
                                    String permissionCode, String label, String description, int sortNo) {
        return new ActionDefinition(pageMenuCode, actionKey, grantMenuCode, permissionCode,
            label, description, ActionMode.LINKED, false, sortNo);
    }

    private ActionDefinition sensitive(String pageMenuCode, String actionKey, String grantMenuCode,
                                       String permissionCode, String label, String description, int sortNo) {
        return new ActionDefinition(pageMenuCode, actionKey, grantMenuCode, permissionCode,
            label, description, ActionMode.LINKED, true, sortNo);
    }

    private ActionDefinition uiOnly(String pageMenuCode, String actionKey, String label,
                                    String description, int sortNo) {
        return new ActionDefinition(pageMenuCode, actionKey, null, null,
            label, description, ActionMode.UI_ONLY, false, sortNo);
    }
}
