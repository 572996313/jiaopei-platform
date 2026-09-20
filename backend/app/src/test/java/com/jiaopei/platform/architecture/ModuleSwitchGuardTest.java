package com.jiaopei.platform.architecture;

import com.jiaopei.platform.JiaopeiPlatformApplication;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块开关前提守护（纯反射/资源检查，不起上下文、不连数据库）。
 *
 * <ol>
 *   <li>app 启动类的底座扫描清单不得含业务包：放宽回 {@code com.jiaopei.platform} 会把业务
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
        SpringBootApplication annotation = JiaopeiPlatformApplication.class
            .getAnnotation(SpringBootApplication.class);
        assertThat(annotation).as("启动类必须保留 @SpringBootApplication 的显式扫描清单").isNotNull();

        assertThat(Arrays.asList(annotation.scanBasePackages()))
            .as("底座扫描清单不得包含业务包，否则模块开关失效")
            .noneMatch(pkg -> pkg.startsWith("com.jiaopei.platform.organization")
                || pkg.startsWith("com.jiaopei.platform.education"));
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
}
