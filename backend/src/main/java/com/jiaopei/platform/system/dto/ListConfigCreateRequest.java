package com.jiaopei.platform.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "列表页配置创建请求")
public record ListConfigCreateRequest(
    @NotBlank(message = "资源编码不能为空")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,62}$", message = "资源编码仅支持小写字母、数字和中划线,且以字母或数字开头")
    @Schema(description = "资源编码,列表页唯一标识,如 system-users") String resourceCode,

    @NotBlank(message = "资源名称不能为空") @Size(max = 64, message = "资源名称长度不能超过64个字符")
    @Schema(description = "资源名称,如 用户管理") String resourceName,

    @NotBlank(message = "接口路径不能为空") @Size(max = 128, message = "接口路径长度不能超过128个字符")
    @Schema(description = "分页查询接口路径,如 /system/users/page") String endpoint,

    @NotNull(message = "默认每页条数不能为空") @Min(value = 1, message = "默认每页条数不能小于1") @Max(value = 100, message = "默认每页条数不能超过100")
    @Schema(description = "默认每页条数") Integer pageSize,

    @NotBlank(message = "列配置不能为空") @Schema(description = "列配置 JSON 数组") String columnsJson,

    @NotBlank(message = "查询条件配置不能为空") @Schema(description = "查询条件配置 JSON 数组") String queryJson,

    @Schema(description = "按钮配置 JSON 数组,为空时前端回退插槽模式") String actionsJson,

    @NotBlank(message = "状态不能为空") @Size(max = 32, message = "状态长度不能超过32个字符")
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"}) String status,

    @Size(max = 500, message = "备注长度不能超过500个字符") @Schema(description = "备注") String remark
) {
}
