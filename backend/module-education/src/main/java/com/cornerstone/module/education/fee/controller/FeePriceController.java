package com.cornerstone.module.education.fee.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.fee.domain.entity.FeePriceEntity;
import com.cornerstone.module.education.fee.dto.FeePriceCreateRequest;
import com.cornerstone.module.education.fee.dto.FeePriceUpdateRequest;
import com.cornerstone.module.education.fee.service.FeePriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/education/fee-prices")
@Tag(name = "收费管理")
public class FeePriceController {

    private final FeePriceService feePriceService;

    public FeePriceController(FeePriceService feePriceService) {
        this.feePriceService = feePriceService;
    }

    @Operation(summary = "获取收费价目列表", description = "四个维度 0=不限；受限校区角色同时能看到全校区通用价目")
    @GetMapping
    public ApiResponse<List<FeePriceEntity>> list(@RequestParam(required = false) String institutionId,
                                                  @RequestParam(required = false) String feeItemId,
                                                  @RequestParam(required = false) String campusId) {
        return ApiResponse.success("收费价目列表已加载", feePriceService.listPrices(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(feeItemId, "feeItemId"),
            ExternalIdParser.parseOptional(campusId, "campusId")));
    }

    @Operation(summary = "创建收费价目", description = "按学员 > 班级 > 校区 > 学期 > 全局的特异度取价；同一组维度不能重复")
    @PostMapping
    @RequiresPermission("education:fee:config:write")
    public ApiResponse<FeePriceEntity> create(@Valid @RequestBody FeePriceCreateRequest request) {
        return ApiResponse.success("收费价目已创建", feePriceService.createPrice(request));
    }

    @Operation(summary = "更新收费价目")
    @PutMapping("/{id}")
    @RequiresPermission("education:fee:config:write")
    public ApiResponse<FeePriceEntity> update(@PathVariable Long id,
                                              @Valid @RequestBody FeePriceUpdateRequest request) {
        return ApiResponse.success("收费价目已更新", feePriceService.updatePrice(id, request));
    }

    @Operation(summary = "删除收费价目")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:fee:config:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        feePriceService.deletePrice(id);
        return ApiResponse.success("收费价目已删除", null);
    }
}
