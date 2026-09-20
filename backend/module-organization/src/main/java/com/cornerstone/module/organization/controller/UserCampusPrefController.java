package com.cornerstone.module.organization.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.organization.dto.UserCampusPrefUpdateRequest;
import com.cornerstone.module.organization.dto.UserCampusPrefView;
import com.cornerstone.module.organization.service.UserCampusPrefService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户默认校区偏好接口。
 *
 * <p>校区管理页按校区查看"哪些账号默认落在本校区"并就地调整；
 * 工作台初始化默认范围走 /me，只需登录态、不需要管理权限。</p>
 */
@RestController
@RequestMapping("/api/organizations/campus-preferences")
@Tag(name = "组织管理")
public class UserCampusPrefController {

    private final UserCampusPrefService userCampusPrefService;

    public UserCampusPrefController(UserCampusPrefService userCampusPrefService) {
        this.userCampusPrefService = userCampusPrefService;
    }

    @Operation(summary = "获取默认校区偏好列表", description = "按校区筛选(仅限登录用户所属机构),返回默认校区为该校区或全部已设置的账号")
    @GetMapping
    @RequiresPermission("organization:read")
    public ApiResponse<List<UserCampusPrefView>> list(@RequestParam(required = false) String campusId) {
        Long institutionId = AuthContext.requireUser().orgId();
        return ApiResponse.success("默认校区偏好已加载", userCampusPrefService.listByCampus(
            institutionId, ExternalIdParser.parseOptional(campusId, "campusId")));
    }

    @Operation(summary = "获取当前用户的默认校区", description = "供管理端初始化范围筛选使用,仅需登录态")
    @GetMapping("/me")
    public ApiResponse<UserCampusPrefView> myPreference() {
        Long userId = AuthContext.requireUser().userId();
        Long institutionId = AuthContext.requireUser().orgId();
        return ApiResponse.success("默认校区已加载", userCampusPrefService.preference(institutionId, userId));
    }

    @Operation(summary = "设置用户默认校区", description = "campusId 传 null 表示清除;目标账号必须属于登录用户所在机构")
    @PutMapping("/{userId}")
    @RequiresPermission("organization:write")
    public ApiResponse<UserCampusPrefView> save(@PathVariable Long userId,
                                                @RequestBody UserCampusPrefUpdateRequest request) {
        Long institutionId = AuthContext.requireUser().orgId();
        return ApiResponse.success("默认校区已更新",
            userCampusPrefService.save(institutionId, userId, request.campusId()));
    }
}
