package com.cornerstone.core.config;

import com.cornerstone.core.system.module.ModuleDescriptor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档全局信息。
 *
 * <p>接口分组说明由各 Controller 的 {@code @Tag} 自带，这里不再按模块硬编码 tag；
 * 文档首屏的"已装配模块"取自 {@link ModuleDescriptor} 注册表，模块增减无需改本类。</p>
 */
@Configuration
public class OpenApiConfig {

    private final ObjectProvider<ModuleDescriptor> moduleDescriptors;

    public OpenApiConfig(ObjectProvider<ModuleDescriptor> moduleDescriptors) {
        this.moduleDescriptors = moduleDescriptors;
    }

    @Bean
    public OpenAPI jiaopeiOpenApi() {
        String assembledModules = moduleDescriptors.orderedStream()
            .map(ModuleDescriptor::name)
            .collect(Collectors.joining("、"));
        return new OpenAPI()
            .info(new Info()
                .title("教培管理平台 API")
                .description("教培管理平台后端核心接口。已装配模块：" + assembledModules)
                .version("v1")
                .license(new License().name("专有许可")))
            .servers(List.of(new Server().url("/").description("当前环境")))
            .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                .name("系统管理")
                .description("系统健康检查与启动信息接口"));
    }
}
