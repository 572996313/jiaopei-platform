package com.jiaopei.platform.system.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SystemMenuEntity extends BaseEntity {

    private Long parentId;
    private String menuCode;
    private String menuName;
    private String menuType;
    private String path;
    private String component;
    private String permissionCode;
    private String icon;
    private Integer sortNo;
    private Boolean visible;
    private String status;
    private String remark;
}
