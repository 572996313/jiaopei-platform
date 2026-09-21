package com.cornerstone.architecture;

import com.cornerstone.CornerstoneApplication;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块开关前提守护（纯反射/资源检查，不起上下文、不连数据库）。
 *
 * <ol>
 *   <li>app 启动类的底座扫描清单不得含业务包：放宽回 {@code com.cornerstone} 会把业务
 *       {@code @Component} 无条件扫进来，各模块的 {@code @ConditionalOnProperty} 就形同虚设。</li>
 *   <li>业务模块必须以自动配置形式装配（jar 内要有 AutoConfiguration.imports），
 *       这样"加/减模块=改 pom"才成立，同时也保证 {@code @WebMvcTest} 切片不会误加载整模块。</li>
 * </ol>
 */
class ModuleSwitchGuardTest {

    private static final String AUTO_CONFIGURATION_IMPORTS =
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    void appMustNotComponentScanBusinessPackages() {
        SpringBootApplication annotation = CornerstoneApplication.class
            .getAnnotation(SpringBootApplication.class);
        assertThat(annotation).as("启动类必须保留 @SpringBootApplication 的显式扫描清单").isNotNull();

        assertThat(Arrays.asList(annotation.scanBasePackages()))
            .as("底座扫描清单不得包含业务包，否则模块开关失效")
            .noneMatch(pkg -> pkg.startsWith("com.cornerstone.module.organization")
                || pkg.startsWith("com.cornerstone.module.education"));
    }

    @Test
    void businessModulesMustShipAutoConfigurationImports() throws Exception {
        // classpath 上每个模块 jar 各有一份同名 imports 文件，取全部内容的并集来断言。
        String all = Collections.list(getClass().getClassLoader()
                .getResources(AUTO_CONFIGURATION_IMPORTS))
            .stream()
            .map(url -> {
                try (InputStream in = url.openStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })
            .collect(Collectors.joining("\n"));

        assertThat(all).contains("OrganizationModuleAutoConfiguration");
        assertThat(all).contains("EducationModuleAutoConfiguration");
    }

    @Test
    void coreMustSelfRegisterItsConfigurationProperties() {
        // 下游主类包根多半不是 com.cornerstone，靠它写 @ConfigurationPropertiesScan 扫底座包是条
        // 静默失效的隐性契约：漏写就是"cornerstone.* 全部取默认值、启动不报错"。登记动作收进底座，
        // 这里拦住回退——底座新增 @ConfigurationProperties 类却忘了挂进 @EnableConfigurationProperties 也一并拦。
        Set<Class<?>> declared = new LinkedHashSet<>();
        ClassPathScanningCandidateComponentProvider propertyScanner =
            new ClassPathScanningCandidateComponentProvider(false);
        propertyScanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));
        for (var candidate : propertyScanner.findCandidateComponents("com.cornerstone.core")) {
            declared.add(load(candidate));
        }

        Set<Class<?>> registered = new LinkedHashSet<>();
        ClassPathScanningCandidateComponentProvider configScanner =
            new ClassPathScanningCandidateComponentProvider(false);
        configScanner.addIncludeFilter(new AnnotationTypeFilter(Configuration.class));
        for (var candidate : configScanner.findCandidateComponents("com.cornerstone.core")) {
            EnableConfigurationProperties annotation =
                load(candidate).getAnnotation(EnableConfigurationProperties.class);
            if (annotation != null) {
                registered.addAll(Arrays.asList(annotation.value()));
            }
        }

        assertThat(declared).as("扫不到底座的 @ConfigurationProperties，本测试随之失效").isNotEmpty();
        assertThat(registered)
            .as("底座必须自己登记全部配置类，不得要求下游扫 com.cornerstone.core.config.properties")
            .containsAll(declared);
    }

    private Class<?> load(BeanDefinition candidate) {
        try {
            return Class.forName(candidate.getBeanClassName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("配置类扫不到:" + candidate.getBeanClassName(), e);
        }
    }
}
