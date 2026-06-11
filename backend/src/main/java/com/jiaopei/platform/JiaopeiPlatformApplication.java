package com.jiaopei.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JiaopeiPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiaopeiPlatformApplication.class, args);
    }
}
