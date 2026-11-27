package com.cornerstone.module.education.config;

import com.cornerstone.core.config.MybatisPlusConfig;
import com.cornerstone.module.organization.config.OrganizationModuleAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 教务模块（教师/学员/班级/课程/报名/排课/考勤/小程序）自动装配。
 *
 * <p>开关：{@code cornerstone.modules.enabled.education}，缺省视为开启。关闭后本包内
 * Controller/Service/Mapper/事件监听都不注册，对应接口 404。</p>
 *
 * <p>走自动配置的原因同组织模块：开关要包住整包扫描，且 {@code @WebMvcTest} 切片会关掉自动配置。
 * 在组织模块之后装配，保证它注册的 Bean 晚于 organization 的（仅影响展示顺序，不构成依赖）。</p>
 */
@AutoConfiguration(after = OrganizationModuleAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "cornerstone.modules.enabled",
    name = "education",
    havingValue = "true",
    matchIfMissing = true)
@ComponentScan(
    basePackages = "com.cornerstone.module.education",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.CUSTOM,
        classes = AutoConfigurationExcludeFilter.class))
@MapperScan(
    basePackages = {
        "com.cornerstone.module.education.mapper",
        "com.cornerstone.module.education.fee.mapper",
        "com.cornerstone.module.education.message.mapper"
    },
    sqlSessionFactoryRef = MybatisPlusConfig.PRIMARY_FACTORY,
    sqlSessionTemplateRef = MybatisPlusConfig.PRIMARY_SESSION)
public class EducationModuleAutoConfiguration {
}
