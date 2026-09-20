package com.jiaopei.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 组装启动入口：本模块不含业务代码，只声明"这个项目装配了哪些模块"。
 *
 * <p>底座扫描清单写在这里（{@code scanBasePackages} 只列底座六个顶层包）。业务模块
 * 不在这里出现：各业务模块 jar 通过自己的 {@code META-INF/spring/...AutoConfiguration.imports}
 * 自动装配，并由自身配置类上的 {@code @ConditionalOnProperty} 决定开关，
 * 所以增删业务模块只改 pom（+ 需要时改 yml），不用动本类。</p>
 *
 * <p>不要把 {@code scanBasePackages} 放宽回 {@code com.jiaopei.platform}：那等于无条件
 * 扫进 classpath 上所有业务类，模块开关就形同虚设了。底座新增顶层包时要同步补这里，
 * 漏改由 ArchUnit 的 productionClassesMustResideInAssembledPackages 规则拦。</p>
 */
@SpringBootApplication(scanBasePackages = {
    "com.jiaopei.platform.auth",
    "com.jiaopei.platform.system",
    "com.jiaopei.platform.tenant",
    "com.jiaopei.platform.audit",
    "com.jiaopei.platform.common",
    "com.jiaopei.platform.config"
})
@ConfigurationPropertiesScan
public class JiaopeiPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiaopeiPlatformApplication.class, args);
    }
}
