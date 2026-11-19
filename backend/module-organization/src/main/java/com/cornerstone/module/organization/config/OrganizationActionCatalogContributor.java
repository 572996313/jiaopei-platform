package com.cornerstone.module.organization.config;

import com.cornerstone.core.system.action.ActionCatalogContributor;
import com.cornerstone.core.system.action.ActionDefinition;
import com.cornerstone.core.system.action.ActionMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 组织模块（校区/学期/教室/时间段/校区默认账号）的按钮授权项登记。
 *
 * <p>这些页面的写操作共用 {@code organization:write} 与「组织档案维护」按钮节点（116），
 * 现阶段一律登记为 {@link ActionMode#LINKED}：客户能看到每个按钮，但勾一个会联动同码按钮。
 * 待权限码按页面拆分后，再逐条改为 {@link ActionMode#STANDALONE}。</p>
 */
@Component("organizationActionCatalogContributor")
public class OrganizationActionCatalogContributor implements ActionCatalogContributor {

    private static final String PAGE_CAMPUS = "organization_campus";
    private static final String PAGE_CLASSROOMS = "organization_classrooms";
    private static final String PAGE_TIME_SLOTS = "organization_time_slots";
    private static final String PAGE_TIME_SLOT_LIST = "organization_time_slot_list";
    private static final String PAGE_CAMPUS_ACCOUNTS = "organization_campus_accounts";
    private static final String GRANT_WRITE = "organization_master_data_write";
    private static final String WRITE = "organization:write";

    @Override
    public String moduleKey() {
        return "organization";
    }

    @Override
    public List<ActionDefinition> actions() {
        return List.of(
            // 校区与学期
            linked(PAGE_CAMPUS, "campus-create", "新增校区", "建立新的校区档案", 10),
            linked(PAGE_CAMPUS, "campus-edit", "编辑校区", "修改校区名称、编码与状态", 20),
            linked(PAGE_CAMPUS, "campus-delete", "删除校区", "删除未被引用的校区", 30),
            linked(PAGE_CAMPUS, "term-create", "新增学期", "为校区建立学期", 40),
            linked(PAGE_CAMPUS, "term-edit", "编辑学期", "修改学期名称与起止日期", 50),
            linked(PAGE_CAMPUS, "term-delete", "删除学期", "删除未被班级引用的学期", 60),

            // 教室
            linked(PAGE_CLASSROOMS, "classroom-create", "新增教室", "建立教室档案并设置容量", 10),
            linked(PAGE_CLASSROOMS, "classroom-edit", "编辑教室", "修改教室名称、容量与状态", 20),
            linked(PAGE_CLASSROOMS, "classroom-delete", "删除教室", "删除未被课次引用的教室", 30),

            // 时段模板
            linked(PAGE_TIME_SLOTS, "time-slot-template-create", "新建模板", "建立一套节次模板", 10),
            linked(PAGE_TIME_SLOTS, "time-slot-template-delete", "删除模板", "删除模板及其节次定义", 20),
            linked(PAGE_TIME_SLOTS, "time-slot-generate", "按参数生成", "按起止时间与节次时长生成网格草稿", 30),
            linked(PAGE_TIME_SLOTS, "time-slot-save", "保存节次模板", "把当前网格写入节次定义", 40),

            // 节次明细
            linked(PAGE_TIME_SLOT_LIST, "time-slot-create", "新增节次", "新增一个上课时段", 10),
            linked(PAGE_TIME_SLOT_LIST, "time-slot-edit", "编辑节次", "修改节次起止时间", 20),
            linked(PAGE_TIME_SLOT_LIST, "time-slot-delete", "删除节次", "被课次引用时只能停用", 30),
            sensitive(PAGE_TIME_SLOT_LIST, "time-slot-clean-references", "清理已删课次引用",
                "删除引用该节次的已删除课次及其考勤，不可恢复", 40),

            // 校区默认账号
            linked(PAGE_CAMPUS_ACCOUNTS, "campus-account-set-default", "设为本校区默认",
                "指定该校区新增档案时默认归属的登录账号", 10)
        );
    }

    private ActionDefinition linked(String pageMenuCode, String actionKey, String label,
                                    String description, int sortNo) {
        return new ActionDefinition(pageMenuCode, actionKey, GRANT_WRITE, WRITE,
            label, description, ActionMode.LINKED, false, sortNo);
    }

    private ActionDefinition sensitive(String pageMenuCode, String actionKey, String label,
                                       String description, int sortNo) {
        return new ActionDefinition(pageMenuCode, actionKey, GRANT_WRITE, WRITE,
            label, description, ActionMode.LINKED, true, sortNo);
    }
}
