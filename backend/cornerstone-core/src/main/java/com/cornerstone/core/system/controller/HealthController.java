package com.cornerstone.core.system.controller;

import com.cornerstone.core.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Tag(name = "系统管理")
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @Operation(summary = "健康检查")
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success("健康检查通过", Map.of(
            "service", applicationName,
            "status", "正常",
            "timestamp", OffsetDateTime.now()
        ));
    }
}
