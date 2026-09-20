package com.cornerstone.architecture;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构守护测试：把 AGENTS.md 中的接口文档/权限/分层规范固化为可执行规则。
 * 违反即 mvn test 失败，替代人工 grep 自查。
 */
class ArchitectureRulesTest {

    private static JavaClasses productionClasses;

    private static final DescribedPredicate<JavaMethod> HTTP_MAPPING_METHODS =
        new DescribedPredicate<>("被 HTTP 映射注解标注的方法") {
            @Override
            public boolean test(JavaMethod method) {
                return method.isAnnotatedWith(GetMapping.class)
                    || method.isAnnotatedWith(PostMapping.class)
                    || method.isAnnotatedWith(PutMapping.class)
                    || method.isAnnotatedWith(DeleteMapping.class)
                    || method.isAnnotatedWith(PatchMapping.class);
            }
        };

    private static final DescribedPredicate<JavaMethod> WRITE_MAPPING_METHODS =
        new DescribedPredicate<>("写操作方法（POST/PUT/DELETE/PATCH）") {
            @Override
            public boolean test(JavaMethod method) {
                return method.isAnnotatedWith(PostMapping.class)
                    || method.isAnnotatedWith(PutMapping.class)
                    || method.isAnnotatedWith(DeleteMapping.class)
                    || method.isAnnotatedWith(PatchMapping.class);
            }
        };

    /** auth 自助接口（登录/登出/改密/小程序绑定）按设计不带权限注解，靠身份态自身兜底 */
    private static final DescribedPredicate<JavaClass> NOT_AUTH_CONTROLLER =
        new DescribedPredicate<>("非 auth 自助接口包") {
            @Override
            public boolean test(JavaClass javaClass) {
                return !javaClass.getPackageName().equals("com.cornerstone.core.auth.controller");
            }
        };

    @BeforeAll
    static void importClasses() {
        productionClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.cornerstone");
    }

    @Test
    void controllerEndpointsMustHaveOperationSummary() {
        methods().that(HTTP_MAPPING_METHODS)
            .and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should().beAnnotatedWith(Operation.class)
            .because("AGENTS.md 接口中文说明规范：Swagger 与功能权限总览页依赖 @Operation.summary 生成接口说明")
            .check(productionClasses);
    }

    @Test
    void writeEndpointsMustRequirePermission() {
        methods().that(WRITE_MAPPING_METHODS)
            .and().areDeclaredInClassesThat(JavaClass.Predicates
                .resideInAnyPackage("com.cornerstone..").and(NOT_AUTH_CONTROLLER))
            .should().beAnnotatedWith(RequiresPermission.class)
            .because("AGENTS.md 安全约定：写接口必须声明 @RequiresPermission（auth 自助接口包除外）")
            .check(productionClasses);
    }

    @Test
    void servicesMustNotDependOnControllers() {
        noClasses().that().resideInAnyPackage("..service..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..")
            .because("分层约定：Service 不得反向依赖 Controller")
            .check(productionClasses);
    }

    @Test
    void entitiesMustResideInDomainPackages() {
        classes().that().areAnnotatedWith(TableName.class)
            .should().resideInAnyPackage("..domain", "..domain..")
            .because("持久化约定：MyBatis-Plus 实体统一放在各模块 domain 包")
            .check(productionClasses);
    }

    /** 底座包（auth/system/common/config/tenant/audit）：换行业后仍可复用的部分，禁止感知任何教培业务 */
    private static final String[] PLATFORM_PACKAGES = {
        "com.cornerstone.core.auth..",
        "com.cornerstone.core.system..",
        "com.cornerstone.core.common..",
        "com.cornerstone.core.config..",
        "com.cornerstone.core.tenant..",
        "com.cornerstone.core.audit.."
    };

    @Test
    void platformMustNotDependOnEducation() {
        noClasses().that().resideInAnyPackage(PLATFORM_PACKAGES)
            .should().dependOnClassesThat().resideInAnyPackage("com.cornerstone.module.education..")
            .because("底座边界：auth/system/common/config 不得依赖教务业务包（见 docs/底座分离与Cornerstone重构计划.md）")
            .check(productionClasses);
    }

    @Test
    void platformMustNotDependOnOrganization() {
        noClasses().that().resideInAnyPackage(PLATFORM_PACKAGES)
            .should().dependOnClassesThat().resideInAnyPackage("com.cornerstone.module.organization..")
            .because("底座边界：auth/system/common/config 不得依赖组织业务包（机构已迁至底座 tenant 包）")
            .check(productionClasses);
    }

    /**
     * 装配清单白名单：app 启动类不再做全包扫描，组件只由各模块配置类的 basePackages 注册。
     * 新增顶层包时必须同步改 {@code CornerstoneCoreConfiguration} 或对应模块的 *ModuleConfiguration，
     * 本规则随之更新——漏改扫描清单会让类静默不进容器，这里提前拦下。
     */
    @Test
    void productionClassesMustResideInAssembledPackages() {
        classes().should().resideInAnyPackage(
            "com.cornerstone",
            "com.cornerstone.core.auth..",
            "com.cornerstone.core.system..",
            "com.cornerstone.core.tenant..",
            "com.cornerstone.core.audit..",
            "com.cornerstone.core.common..",
            "com.cornerstone.core.config..",
            "com.cornerstone.module.organization..",
            "com.cornerstone.module.education..")
            .because("装配清单只扫描已声明的顶层包，新增顶层包要同步加入对应模块配置类的 basePackages")
            .check(productionClasses);
    }
}
