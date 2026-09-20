package com.cornerstone.core.system.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_menu")
public class SystemRoleMenuEntity extends BaseEntity {

    private Long roleId;
    private Long menuId;
}
