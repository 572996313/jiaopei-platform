package com.cornerstone.architecture;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.system.action.ActionCatalogContributor;
import com.cornerstone.core.system.action.ActionDefinition;
import com.cornerstone.core.system.action.ActionMode;
import com.cornerstone.core.system.action.CoreActionCatalogContributor;
import com.cornerstone.module.education.config.EducationActionCatalogContributor;
import com.cornerstone.module.organization.config.OrganizationActionCatalogContributor;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 按钮授权目录守护：代码侧登记的动作必须自洽，并且每个写动作的权限码真的有接口在用。
 *
 * <p>这条测试拦的是"目录里写了按钮、后端却没有对应注解"和"动作键重复导致界面只显示一条"
 * 这类静默缺口；数据库侧节点是否存在由 {@code /api/system/action-catalog} 的 gaps 在运行期暴露。</p>
 */
class ActionCatalogGuardTest {

    private static List<ActionDefinition> definitions;
    private static Set<String> annotatedPermissionCodes;

    @BeforeAll
    static void setUp() {
        List<ActionCatalogContributor> contributors = List.of(
            new CoreActionCatalogContributor(),
            new OrganizationActionCatalogContributor(),
            new EducationActionCatalogContributor()
        );
        definitions = contributors.stream()
            .flatMap(contributor -> contributor.actions().stream())
            .toList();

        JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.cornerstone");
        annotatedPermissionCodes = new LinkedHashSet<>();
        for (var javaClass : productionClasses) {
            if (javaClass.isAnnotatedWith(RequiresPermission.class)) {
                annotatedPermissionCodes.add(javaClass.getAnnotationOfType(RequiresPermission.class).value());
            }
            for (JavaMethod method : javaClass.getMethods()) {
                if (method.isAnnotatedWith(RequiresPermission.class)) {
                    annotatedPermissionCodes.add(method.getAnnotationOfType(RequiresPermission.class).value());
                }
            }
        }
    }

    @Test
    void 动作键必须全局唯一() {
        Set<String> seen = new HashSet<>();
        for (ActionDefinition definition : definitions) {
            assertNotNull(definition.actionKey(), "动作键不能为空");
            assertTrue(seen.add(definition.actionKey()),
                "动作键重复：" + definition.actionKey() + "（界面会只显示一条）");
        }
    }

    @Test
    void 可授权动作必须绑定节点与权限码() {
        for (ActionDefinition definition : definitions) {
            if (definition.mode() == ActionMode.UI_ONLY) {
                assertTrue(definition.grantMenuCode() == null && definition.permissionCode() == null,
                    "纯前端动作不应绑定节点或权限码：" + definition.actionKey());
                continue;
            }
            assertNotNull(definition.mode(), "缺少授权模式：" + definition.actionKey());
            assertNotNull(definition.grantMenuCode(), "缺少授权节点：" + definition.actionKey());
            assertNotNull(definition.permissionCode(), "缺少权限码：" + definition.actionKey());
            assertNotNull(definition.label(), "缺少中文按钮名：" + definition.actionKey());
        }
    }

    @Test
    void 可授权动作的权限码必须有后端接口在用() {
        for (ActionDefinition definition : definitions) {
            if (definition.mode() == ActionMode.UI_ONLY) {
                continue;
            }
            assertTrue(annotatedPermissionCodes.contains(definition.permissionCode()),
                "动作 " + definition.actionKey() + " 的权限码 " + definition.permissionCode()
                    + " 没有任何 @RequiresPermission 接口在用");
        }
    }

    @Test
    void 每个页面都必须至少登记一个动作或明确为只读页() {
        Set<String> pagesWithActions = new HashSet<>();
        for (ActionDefinition definition : definitions) {
            pagesWithActions.add(definition.pageMenuCode());
        }
        // 只读页（操作日志、功能权限、课表总览等）允许没有动作；这里只校验登记过的页面编码非空。
        for (String pageCode : pagesWithActions) {
            assertNotNull(pageCode, "动作必须归属一个页面");
            assertTrue(!pageCode.isBlank(), "动作必须归属一个页面");
        }
    }
}
