package com.cornerstone.core.system.controller;

import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.system.dto.BrandingView;
import com.cornerstone.core.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/branding")
@Tag(name = "系统管理")
public class BrandingController {

    /** 品牌文案系统参数键,值由部署方在自己的迁移里种下,底座只读不写 */
    private static final String BRAND_NAME_KEY = "brand.name";
    private static final String BRAND_TAGLINE_KEY = "brand.tagline";
    private static final String BRAND_LOGIN_COPY_KEY = "brand.login_copy";
    private static final String BRAND_LOGIN_FORM_TITLE_KEY = "brand.login_form_title";

    private final SystemConfigService systemConfigService;

    public BrandingController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @Operation(summary = "获取品牌文案",
        description = "无需登录,返回系统名称等展示文案,取值来自系统参数 brand.*;空串表示该参数未配置,由前端提示维护")
    @GetMapping
    public ApiResponse<BrandingView> branding() {
        String name = read(BRAND_NAME_KEY);
        return ApiResponse.success("已获取品牌文案", new BrandingView(
            name,
            read(BRAND_TAGLINE_KEY),
            markOf(name),
            read(BRAND_LOGIN_COPY_KEY),
            read(BRAND_LOGIN_FORM_TITLE_KEY)
        ));
    }

    /** 配置值可能带首尾空白,统一裁掉后空串即"未配置" */
    private String read(String key) {
        String value = systemConfigService.getConfigValue(key, "");
        return value == null ? "" : value.trim();
    }

    /** 角标跟随系统名称推导,避免"改了名称、角标还是旧字"的两处漂移 */
    private String markOf(String name) {
        return name.isEmpty() ? "" : String.valueOf(name.charAt(0));
    }
}
