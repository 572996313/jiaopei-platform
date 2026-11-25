package com.cornerstone.core.system.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.system.dto.DocumentLineageView;
import com.cornerstone.core.system.service.DocumentLineageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/document-lineage")
@Tag(name = "业务链路", description = "各模块声明的单据生成/汇总/核销关系；只读，不代表某次请求的数据流")
public class DocumentLineageController {

    private final DocumentLineageService documentLineageService;

    public DocumentLineageController(DocumentLineageService documentLineageService) {
        this.documentLineageService = documentLineageService;
    }

    @GetMapping
    @RequiresPermission("system:lineage:read")
    @Operation(summary = "业务链路",
        description = "返回各模块登记的单据节点与关系；confirmed=false 为业务口径待确认项")
    public ApiResponse<DocumentLineageView> lineage() {
        return ApiResponse.success("业务链路已加载", documentLineageService.lineage());
    }
}
