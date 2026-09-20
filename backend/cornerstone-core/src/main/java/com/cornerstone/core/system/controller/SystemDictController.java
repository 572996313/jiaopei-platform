package com.cornerstone.core.system.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.system.dto.DictItemCreateRequest;
import com.cornerstone.core.system.dto.DictItemUpdateRequest;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.dto.DictOptionGroupView;
import com.cornerstone.core.system.dto.DictTypeCreateRequest;
import com.cornerstone.core.system.dto.DictTypeUpdateRequest;
import com.cornerstone.core.system.dto.DictTypeView;
import com.cornerstone.core.system.service.SystemDictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
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
@RequestMapping("/api/system/dicts")
@Tag(name = "系统管理")
public class SystemDictController {

    private final SystemDictService systemDictService;

    public SystemDictController(SystemDictService systemDictService) {
        this.systemDictService = systemDictService;
    }

    @Operation(summary = "获取字典类型列表")
    @GetMapping("/types")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<List<DictTypeView>> types() {
        return ApiResponse.success("字典类型列表已加载", systemDictService.listTypes());
    }

    @Operation(summary = "获取字典类型下的条目")
    @GetMapping("/types/{typeCode}/items")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<List<DictItemView>> items(@PathVariable String typeCode) {
        return ApiResponse.success("字典条目列表已加载", systemDictService.listItems(typeCode));
    }

    @Operation(summary = "字典选项", description = "供业务表单下拉使用,仅需登录,不要求字典管理权限;只返回启用类型的启用条目,typeCodes 为空时返回全部启用类型")
    @GetMapping("/options")
    public ApiResponse<List<DictOptionGroupView>> options(@RequestParam(required = false) String typeCodes) {
        List<String> codes = typeCodes == null || typeCodes.isBlank()
            ? List.of()
            : Arrays.stream(typeCodes.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
        return ApiResponse.success("字典选项已加载", systemDictService.listOptions(codes));
    }

    @Operation(summary = "创建字典类型")
    @PostMapping("/types")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<DictTypeView> createType(@Valid @RequestBody DictTypeCreateRequest request) {
        return ApiResponse.success("字典类型已创建", systemDictService.createType(request));
    }

    @Operation(summary = "更新字典类型")
    @PutMapping("/types/{id}")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<DictTypeView> updateType(@PathVariable Long id, @Valid @RequestBody DictTypeUpdateRequest request) {
        return ApiResponse.success("字典类型已更新", systemDictService.updateType(id, request));
    }

    @Operation(summary = "删除字典类型")
    @DeleteMapping("/types/{id}")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<Void> deleteType(@PathVariable Long id) {
        systemDictService.deleteType(id);
        return ApiResponse.success("字典类型已删除", null);
    }

    @Operation(summary = "创建字典条目")
    @PostMapping("/items")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<DictItemView> createItem(@Valid @RequestBody DictItemCreateRequest request) {
        return ApiResponse.success("字典条目已创建", systemDictService.createItem(request));
    }

    @Operation(summary = "更新字典条目")
    @PutMapping("/items/{id}")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<DictItemView> updateItem(@PathVariable Long id, @Valid @RequestBody DictItemUpdateRequest request) {
        return ApiResponse.success("字典条目已更新", systemDictService.updateItem(id, request));
    }

    @Operation(summary = "删除字典条目")
    @DeleteMapping("/items/{id}")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        systemDictService.deleteItem(id);
        return ApiResponse.success("字典条目已删除", null);
    }
}
