package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "列表页配置视图")
public record ListConfigView(
    @Schema(description = "主键") Long id,
    @Schema(description = "资源编码,列表页唯一标识") String resourceCode,
    @Schema(description = "资源名称") String resourceName,
    @Schema(description = "分页查询接口路径") String endpoint,
    @Schema(description = "默认每页条数") Integer pageSize,
    @Schema(description = "列配置 JSON 数组") String columnsJson,
    @Schema(description = "查询条件配置 JSON 数组") String queryJson,
    @Schema(description = "按钮配置 JSON 数组,为空时前端回退插槽模式") String actionsJson,
    @Schema(description = "弹窗表单配置 JSON(fields 数组),为空表示未配置表单") String formJson,
    @Schema(description = "页面壳配置 JSON(description/emptyText),页面标题取菜单名、条数由分页器承担,为空时前端回退默认壳") String pageJson,
    @Schema(description = "弹窗壳配置 JSON(width/createTitle/editTitle/submitText/cancelText/createMessage/updateMessage/reloadAfterSubmit),为空时前端回退默认弹窗") String dialogJson,
    @Schema(description = "状态(active=启用,disabled=停用)") String status,
    @Schema(description = "备注") String remark
) {
}
