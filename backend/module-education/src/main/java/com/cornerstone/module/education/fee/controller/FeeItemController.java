package com.cornerstone.module.education.fee.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.fee.domain.entity.FeeItemEntity;
import com.cornerstone.module.education.fee.dto.FeeItemCreateRequest;
import com.cornerstone.module.education.fee.dto.FeeItemUpdateRequest;
import com.cornerstone.module.education.fee.service.FeeItemService;
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
@RequestMapping("/api/education/fee-items")
@Tag(name = "收费管理")
public class FeeItemController {

    private final FeeItemService feeItemService;

    public FeeItemController(FeeItemService feeItemService) {
        this.feeItemService = feeItemService;
    }

    @Operation(summary = "获取收费项目列表", description = "按启停状态筛选；排序号决定导出矩阵块内的项目行序")
    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<FeeItemEntity>> list(@RequestParam(required = false) String institutionId,
                                                 @RequestParam(required = false) String status) {
        return ApiResponse.success("收费项目列表已加载", feeItemService.listItems(
            ExternalIdParser.parseOptional(institutionId, "institutionId"), status));
    }

    @Operation(summary = "创建收费项目", description = "编码留空时按编号规则生成；计费方式决定逐月发生额怎么算")
    @PostMapping
    @RequiresPermission("education:fee:config:write")
    public ApiResponse<FeeItemEntity> create(@Valid @RequestBody FeeItemCreateRequest request) {
        return ApiResponse.success("收费项目已创建", feeItemService.createItem(request));
    }

    @Operation(summary = "更新收费项目")
    @PutMapping("/{id}")
    @RequiresPermission("education:fee:config:write")
    public ApiResponse<FeeItemEntity> update(@PathVariable Long id,
                                             @Valid @RequestBody FeeItemUpdateRequest request) {
        return ApiResponse.success("收费项目已更新", feeItemService.updateItem(id, request));
    }

    @Operation(summary = "删除收费项目", description = "已被价目或台账引用时拒绝删除，不再收费请改为停用")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:fee:config:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        feeItemService.deleteItem(id);
        return ApiResponse.success("收费项目已删除", null);
    }
}
