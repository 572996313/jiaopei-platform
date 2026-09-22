package com.cornerstone.core.auth.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.auth.dto.ActivationCodeCreateRequest;
import com.cornerstone.core.auth.dto.InvitationCreateRequest;
import com.cornerstone.core.auth.dto.InvitationPageQuery;
import com.cornerstone.core.auth.dto.InvitationView;
import com.cornerstone.core.auth.service.AuthInvitationService;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/invitations")
@Tag(name = "注册凭证管理")
public class SystemInvitationController {

    private final AuthInvitationService authInvitationService;

    public SystemInvitationController(AuthInvitationService authInvitationService) {
        this.authInvitationService = authInvitationService;
    }

    @Operation(summary = "注册凭证分页列表", description = "支持关键字(凭证码/备注)、类型、状态、机构筛选")
    @GetMapping("/page")
    @RequiresPermission("system:invitation:manage")
    public ApiResponse<PageResult<InvitationView>> page(@Valid @ParameterObject InvitationPageQuery query) {
        return ApiResponse.success("注册凭证分页列表已加载", authInvitationService.pageInvitations(query));
    }

    @Operation(summary = "创建邀请码", description = "生成一次性邀请码,持有者可在注册页自助建号并自动分配指定角色")
    @PostMapping("/invite")
    @RequiresPermission("system:invitation:manage")
    public ApiResponse<InvitationView> createInvite(@Valid @RequestBody InvitationCreateRequest request) {
        return ApiResponse.success("邀请码已创建", authInvitationService.createInvite(request));
    }

    @Operation(summary = "生成激活码", description = "为待激活(pending_activation)账号生成激活码,重复申请返回仍在有效期内的原码")
    @PostMapping("/activation")
    @RequiresPermission("system:invitation:manage")
    public ApiResponse<InvitationView> createActivationCode(@Valid @RequestBody ActivationCodeCreateRequest request) {
        return ApiResponse.success("激活码已生成", authInvitationService.createActivationCode(request));
    }

    @Operation(summary = "停用注册凭证", description = "停用后凭证立即不可用,已使用次数保留供审计")
    @PutMapping("/{id}/disable")
    @RequiresPermission("system:invitation:manage")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        authInvitationService.disable(id);
        return ApiResponse.success("凭证已停用", null);
    }
}
