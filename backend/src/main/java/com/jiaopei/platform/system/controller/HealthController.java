package com.jiaopei.platform.system.controller;

import com.jiaopei.platform.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Tag(name = "System")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success("Health check passed", Map.of(
            "service", "jiaopei-platform-backend",
            "status", "UP",
            "timestamp", OffsetDateTime.now()
        ));
    }
}
