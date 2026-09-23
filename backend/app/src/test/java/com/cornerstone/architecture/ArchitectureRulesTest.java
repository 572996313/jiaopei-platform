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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架构守护测试：把 AGENTS.md 中的接口文档/权限/分层规范固化为可执行规则。
 * 违反即 mvn test 失败，替代人工 grep 自查。
 */
class ArchitectureRulesTest {

    private static JavaClasses productionClasses;

    /** 显式 bean 名只可能写在这些 stereotype 的 value 上（@RestController 的 value 是请求路径，不算 bean 名） */
    private static final String[] BEAN_NAME_STEREOTYPES = {
        "org.springframework.stereotype.Component",
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Repository",
        "org.springframework.stereotype.Controller",
        "org.springframework.context.annotation.Configuration",
    };

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

    /**
     * auth 自助接口（登录/登出/改密/小程序登录注册注销）按设计不带权限注解，靠身份态自身兜底。
     * 按挂载路径判定而不是 Java 包：这类接口归属哪个模块都可能实现（小程序登录在教务模块），
     * 只要仍挂在 /api/auth 下就沿用同一豁免口径。
     */
    private static final DescribedPredicate<JavaClass> NOT_AUTH_CONTROLLER =
        new DescribedPredicate<>("类级路径不以 /api/auth 开头") {
            @Override
            public boolean test(JavaClass javaClass) {
                String[] paths = javaClass.tryGetAnnotationOfType(RequestMapping.class)
                    .map(RequestMapping::value)
                    .orElse(new String[0]);
                return paths.length == 0 || !paths[0].startsWith("/api/auth");
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

    /**
     * 组件 bean 名唯一（默认名 = 类短名首字母小写）：两个模块放同名组件（如各自的 DictValueValidator）
     * 一起装配时容器会直接 ConflictingBeanDefinitionException 起不来，而本仓库没有 @SpringBootTest、
     * 单测都是 Mockito 手工构造，这类冲突只有真启动才暴露，所以在架构层静态拦下。
     * 确需同名时给 stereotype 注解显式写值（@Component("educationDictValueValidator")），规则按显式名判定。
     */
    @Test
    void springComponentBeanNamesMustBeUnique() {
        Map<String, List<String>> classesByBeanName = new HashMap<>();
        productionClasses.stream()
            .filter(ArchitectureRulesTest::isSpringComponent)
            .forEach(javaClass -> classesByBeanName
                .computeIfAbsent(beanNameOf(javaClass), key -> new ArrayList<>())
                .add(javaClass.getName()));

        List<String> conflicts = classesByBeanName.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> entry.getKey() + " <- " + String.join(" / ", entry.getValue()))
            .toList();

        assertTrue(conflicts.isEmpty(),
            "装配约定：组件 bean 名重复会导致容器启动失败，同名组件必须显式声明带模块前缀的 bean 名。冲突项："
                + String.join("; ", conflicts));
    }

    private static boolean isSpringComponent(JavaClass javaClass) {
        // @Service/@RestController/@Configuration 等都以 @Component 为元注解，只认这个根注解
        return javaClass.isMetaAnnotatedWith(Component.class);
    }

    private static String beanNameOf(JavaClass javaClass) {
        // 显式名只可能写在 BEAN_NAME_STEREOTYPES 上（@RestController 的 value 是请求路径，不是 bean 名）
        for (String stereotype : BEAN_NAME_STEREOTYPES) {
            String explicitName = javaClass.tryGetAnnotationOfType(stereotype)
                .flatMap(annotation -> annotation.tryGetExplicitlyDeclaredProperty("value"))
                .map(String::valueOf)
                .filter(name -> !name.isBlank())
                .orElse(null);
            if (explicitName != null) {
                return explicitName;
            }
        }
        String simpleName = javaClass.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
