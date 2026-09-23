package com.cornerstone.core.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 可配置列表页的展示列与查询条件配置,前端通用列表组件按此渲染。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_list_config")
@Schema(description = "列表页配置")
public class ListConfigEntity extends BaseEntity {

    @Schema(description = "资源编码,列表页唯一标识,如 system-users", requiredMode = Schema.RequiredMode.REQUIRED)
    private String resourceCode;

    @Schema(description = "资源名称,如 用户管理", requiredMode = Schema.RequiredMode.REQUIRED)
    private String resourceName;

    @Schema(description = "分页查询接口路径,如 /system/users/page", requiredMode = Schema.RequiredMode.REQUIRED)
    private String endpoint;

    @Schema(description = "默认每页条数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer pageSize;

    @Schema(description = "列配置 JSON 数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private String columnsJson;

    @Schema(description = "查询条件配置 JSON 数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private String queryJson;

    @Schema(description = "按钮配置 JSON 数组,为空时前端回退插槽模式")
    private String actionsJson;

    @Schema(description = "弹窗表单配置 JSON(fields 数组),为空表示该资源未配置表单")
    private String formJson;

    @Schema(description = "页面壳配置 JSON(description/emptyText),页面标题取菜单名、条数由分页器承担,为空时前端回退默认壳")
    private String pageJson;

    @Schema(description = "弹窗壳配置 JSON(width/createTitle/editTitle/submitText/cancelText/createMessage/updateMessage/reloadAfterSubmit),为空时前端回退默认弹窗")
    private String dialogJson;

    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    private String status;

    @Schema(description = "备注")
    private String remark;
}
