package com.jiaopei.platform.config;

import com.jiaopei.platform.config.properties.JiaopeiProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private final JiaopeiProperties properties;

    public OpenApiConfig(JiaopeiProperties properties) {
        this.properties = properties;
    }

    @Bean
    public OpenAPI jiaopeiOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("教培管理平台 API")
                .description("教培管理平台后端核心接口。")
                .version("v1")
                .license(new License().name("专有许可")))
            .servers(List.of(new Server().url("/").description("当前环境")))
            .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                .name("系统管理")
                .description("系统健康检查与启动信息接口"))
            .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                .name("组织管理")
                .description("组织模块启动信息接口。Enabled="
                    + properties.getModules().isOrganizationEnabled()))
            .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                .name("教务管理")
                .description("教务模块预留接口。Enabled="
                    + properties.getModules().isEducationEnabled()));
    }
}
