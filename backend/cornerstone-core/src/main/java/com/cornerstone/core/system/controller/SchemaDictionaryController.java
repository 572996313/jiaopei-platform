package com.cornerstone.core.system.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.system.dto.SchemaDictionaryView;
import com.cornerstone.core.system.service.SchemaDictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/schema-dictionary")
@Tag(name = "数据字典", description = "库表结构与字段含义的只读视图：库结构读 INFORMATION_SCHEMA，字段含义读实体注解，不维护手写文档")
public class SchemaDictionaryController {

    private final SchemaDictionaryService schemaDictionaryService;

    public SchemaDictionaryController(SchemaDictionaryService schemaDictionaryService) {
        this.schemaDictionaryService = schemaDictionaryService;
    }

    @GetMapping
    @RequiresPermission("system:schema:read")
    @Operation(summary = "数据字典", description = "一次返回全库表与字段：列名/类型/可空/默认值/中文注释/主键、"
        + "对应实体字段与 @Schema 说明、所属模块、维护入口。结果进程内缓存 60 秒；结构只读，改结构一律走 Flyway")
    public ApiResponse<SchemaDictionaryView> dictionary() {
        return ApiResponse.success("数据字典已加载", schemaDictionaryService.dictionary());
    }
}
