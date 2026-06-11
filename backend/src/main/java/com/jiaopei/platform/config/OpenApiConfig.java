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
                .title("JiaoPei Platform API")
                .description("Core backend APIs for the JiaoPei management platform.")
                .version("v1")
                .license(new License().name("Proprietary")))
            .servers(List.of(new Server().url("/").description("Current environment")))
            .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                .name("System")
                .description("System health and bootstrap endpoints"))
            .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                .name("Organization")
                .description("Organization module bootstrap APIs. Enabled="
                    + properties.getModules().isOrganizationEnabled()))
            .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                .name("Education")
                .description("Education module reserved APIs. Enabled="
                    + properties.getModules().isEducationEnabled()));
    }
}
