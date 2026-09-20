package com.cornerstone.core.system.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
@Schema(description = "系统菜单")
public class SystemMenuEntity extends BaseEntity {

    @Schema(description = "菜单归属端(admin=平台用户/管理后台;业务模块注册的 PortalResolver 可扩展其他端,本项目小程序端为 student/parent)")
    private String portal;

    @Schema(description = "父菜单ID")
    private Long parentId;
    @Schema(description = "菜单编码(唯一)")
    private String menuCode;
    @Schema(description = "菜单名称")
    private String menuName;
    @Schema(description = "菜单类型(catalog=目录,menu=菜单/页面,button=按钮)")
    private String menuType;
    @Schema(description = "前端路由路径")
    private String path;
    @Schema(description = "前端组件路径")
    private String component;
    @Schema(description = "权限码(按钮节点承载API权限)")
    private String permissionCode;
    @Schema(description = "图标名称")
    private String icon;
    @Schema(description = "排序号")
    private Integer sortNo;
    @Schema(description = "是否可见")
    private Boolean visible;
    @Schema(description = "状态(active=启用,disabled=停用)")
    private String status;
    @Schema(description = "备注")
    private String remark;
}
