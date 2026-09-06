package com.jiaopei.platform.system.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.system.dto.DictItemCreateRequest;
import com.jiaopei.platform.system.dto.DictItemUpdateRequest;
import com.jiaopei.platform.system.dto.DictItemView;
import com.jiaopei.platform.system.dto.DictTypeCreateRequest;
import com.jiaopei.platform.system.dto.DictTypeUpdateRequest;
import com.jiaopei.platform.system.dto.DictTypeView;
import com.jiaopei.platform.system.service.SystemDictService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/dicts")
@Tag(name = "系统管理")
public class SystemDictController {

    private final SystemDictService systemDictService;

    public SystemDictController(SystemDictService systemDictService) {
        this.systemDictService = systemDictService;
    }

    @GetMapping("/types")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<List<DictTypeView>> types() {
        return ApiResponse.success("字典类型列表已加载", systemDictService.listTypes());
    }

    @GetMapping("/types/{typeCode}/items")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<List<DictItemView>> items(@PathVariable String typeCode) {
        return ApiResponse.success("字典条目列表已加载", systemDictService.listItems(typeCode));
    }

    @PostMapping("/types")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<DictTypeView> createType(@Valid @RequestBody DictTypeCreateRequest request) {
        return ApiResponse.success("字典类型已创建", systemDictService.createType(request));
    }

    @PutMapping("/types/{id}")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<DictTypeView> updateType(@PathVariable Long id, @Valid @RequestBody DictTypeUpdateRequest request) {
        return ApiResponse.success("字典类型已更新", systemDictService.updateType(id, request));
    }

    @PostMapping("/items")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<DictItemView> createItem(@Valid @RequestBody DictItemCreateRequest request) {
        return ApiResponse.success("字典条目已创建", systemDictService.createItem(request));
    }

    @PutMapping("/items/{id}")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<DictItemView> updateItem(@PathVariable Long id, @Valid @RequestBody DictItemUpdateRequest request) {
        return ApiResponse.success("字典条目已更新", systemDictService.updateItem(id, request));
    }

    @DeleteMapping("/items/{id}")
    @RequiresPermission("system:dict:manage")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        systemDictService.deleteItem(id);
        return ApiResponse.success("字典条目已删除", null);
    }
}
