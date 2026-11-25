package com.cornerstone.core.auth.controller;

import com.cornerstone.core.auth.dto.UserIdentityView;
import com.cornerstone.core.auth.service.ThirdPartyLoginService;
import com.cornerstone.core.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录账号的第三方身份绑定管理（自助接口，靠登录态兜底，不挂权限码）。
 */
@RestController
@RequestMapping("/api/auth/me/identities")
@Tag(name = "系统管理")
public class UserIdentityController {

    private final ThirdPartyLoginService thirdPartyLoginService;

    public UserIdentityController(ThirdPartyLoginService thirdPartyLoginService) {
        this.thirdPartyLoginService = thirdPartyLoginService;
    }

    @Operation(summary = "获取本人第三方绑定", description = "返回当前账号已绑定的第三方身份")
    @GetMapping
    public ApiResponse<List<UserIdentityView>> list() {
        return ApiResponse.success("已获取第三方绑定", thirdPartyLoginService.listIdentities());
    }

    @Operation(summary = "解绑本人第三方身份", description = "物理删除绑定关系，可再次绑定")
    @DeleteMapping("/{identityId}")
    public ApiResponse<Void> unbind(@PathVariable Long identityId) {
        thirdPartyLoginService.unbind(identityId);
        return ApiResponse.success("已解除绑定", null);
    }
}
