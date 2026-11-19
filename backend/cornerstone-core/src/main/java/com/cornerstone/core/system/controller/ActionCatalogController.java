package com.cornerstone.core.system.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.system.dto.ActionCatalogView;
import com.cornerstone.core.system.service.ActionCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/action-catalog")
@Tag(name = "系统管理")
public class ActionCatalogController {

    private final ActionCatalogService actionCatalogService;

    public ActionCatalogController(ActionCatalogService actionCatalogService) {
        this.actionCatalogService = actionCatalogService;
    }

    @Operation(summary = "获取按钮授权目录",
        description = "按页面分组返回可授权的按钮/操作项；传 roleId 时附带该角色当前的勾选状态")
    @GetMapping
    @RequiresPermission("system:role:manage")
    public ApiResponse<ActionCatalogView> catalog(@RequestParam(required = false) Long roleId) {
        return ApiResponse.success("按钮授权目录已加载", actionCatalogService.catalog(roleId));
    }
}
