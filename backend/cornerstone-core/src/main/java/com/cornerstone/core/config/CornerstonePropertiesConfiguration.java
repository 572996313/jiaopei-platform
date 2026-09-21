package com.cornerstone.core.config;

import com.cornerstone.core.config.properties.CornerstoneProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 由底座自己登记 {@link CornerstoneProperties}，不要求下游启动类写 {@code @ConfigurationPropertiesScan}。
 *
 * <p>下游主类的包根通常不是 {@code com.cornerstone}（如 {@code com.achievement}），而
 * {@code @ConfigurationPropertiesScan} 默认只扫主类所在包。过去必须由下游显式列出
 * {@code com.cornerstone.core.config.properties}，漏写的症状是"yml 里写了 {@code cornerstone.*}
 * 却全部取默认值"，且不报错、配置越少越像正常启动。注解写在这里之后，这条隐性契约出底座。</p>
 *
 * <p>必须放在 {@code com.cornerstone.core.config}：这是底座六个顶层包之一，下游要用底座就一定会
 * 把它列进 {@code scanBasePackages}（数据源、Flyway、鉴权链都在这条路上）。放在自动配置里反而不行——
 * 底座的 DataSource 必须早于 Spring Boot 与 MyBatis-Plus 的自动配置出现。</p>
 *
 * <p>下游若自己也写了那一条 {@code @ConfigurationPropertiesScan}，Spring Boot 会按同一个 bean 名
 * 去重，不会重复注册，不需要两边都改。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CornerstoneProperties.class)
public class CornerstonePropertiesConfiguration {
}
