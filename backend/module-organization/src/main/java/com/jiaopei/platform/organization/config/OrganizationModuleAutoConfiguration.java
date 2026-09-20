package com.jiaopei.platform.organization.config;

import com.jiaopei.platform.config.MybatisPlusConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 组织主数据模块（校区/教室/学期/时间段）自动装配。
 *
 * <p>开关：{@code jiaopei.modules.organization-enabled}，缺省视为开启。关闭后本包内
 * Controller/Service/Mapper 都不注册，对应接口 404，「功能权限总览」也不再列出这些接口。</p>
 *
 * <p>走自动配置而不是被 app 启动类扫描，有两个必要：</p>
 * <ol>
 *   <li>开关要生效，扫描动作必须整体挂在条件上（散在业务包里的 {@code @Component} 没人扫就装不进容器）。</li>
 *   <li>测试里的 {@code @WebMvcTest} 切片会关闭自动配置，模块因此不会污染切片上下文。</li>
 * </ol>
 *
 * <p>依赖方向：education 依赖本模块，关闭本模块时必须同时关闭 education。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "jiaopei.modules",
    name = "organization-enabled",
    havingValue = "true",
    matchIfMissing = true)
@ComponentScan(
    basePackages = "com.jiaopei.platform.organization",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.CUSTOM,
        classes = AutoConfigurationExcludeFilter.class))
@MapperScan(
    basePackages = "com.jiaopei.platform.organization.mapper",
    sqlSessionFactoryRef = MybatisPlusConfig.PRIMARY_FACTORY,
    sqlSessionTemplateRef = MybatisPlusConfig.PRIMARY_SESSION)
public class OrganizationModuleAutoConfiguration {
}
