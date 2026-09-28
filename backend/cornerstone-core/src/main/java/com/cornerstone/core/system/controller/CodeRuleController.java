package com.cornerstone.core.system.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.system.dto.CodeRuleUpdateRequest;
import com.cornerstone.core.system.dto.CodeRuleView;
import com.cornerstone.core.system.service.CodeRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 编号规则维护与取号入口。
 *
 * <p>读写共用一个权限码 {@code system:code-rule:manage}（与数据源配置同口径：一码覆盖整页，
 * 页面节点与写权限等价），所以维护页只挂一条按钮节点即可。</p>
 *
 * <p>{@code GET /next} 刻意不设权限码、只需登录：它是每个主数据新增页上那颗"生成"按钮，
 * 教师也要能给自己填编码，挂上 system 前缀等于逼机构管理员去勾一个他不一定有的系统页。
 * 它只回一个尚未占用的建议号，不读出任何一行数据。</p>
 */
@RestController
@RequestMapping("/api/system/code-rules")
@Validated
@Tag(name = "编号规则", description = "实体编号的格式维护与快捷取号")
public class CodeRuleController {

    private final CodeRuleService codeRuleService;

    public CodeRuleController(CodeRuleService codeRuleService) {
        this.codeRuleService = codeRuleService;
    }

    @GetMapping
    @RequiresPermission("system:code-rule:manage")
    @Operation(summary = "获取编号规则清单", description = "列出代码登记的全部编码资源及其规则,启用的资源带下一个号预览")
    public ApiResponse<List<CodeRuleView>> list() {
        return ApiResponse.success("编号规则已加载", codeRuleService.listRules());
    }

    @PutMapping("/{resourceCode}")
    @RequiresPermission("system:code-rule:manage")
    @Operation(summary = "保存编号规则", description = "按资源码新增或修改规则;资源码必须是代码里登记过的资源")
    public ApiResponse<CodeRuleView> update(@PathVariable String resourceCode,
                                            @Valid @RequestBody CodeRuleUpdateRequest request) {
        return ApiResponse.success("编号规则已保存", codeRuleService.updateRule(resourceCode, request));
    }

    @GetMapping("/next")
    @Operation(summary = "取下一个建议编号", description = "新增页快捷填号入口,只需登录;只读不落库,未启用该资源时报错")
    public ApiResponse<String> next(@RequestParam(name = "resource") @NotBlank(message = "编码资源码不能为空") String resource) {
        return ApiResponse.success("编号已生成", codeRuleService.nextCode(resource));
    }
}
