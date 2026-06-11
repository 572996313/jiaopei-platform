package com.jiaopei.platform.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_role")
public class RoleEntity extends BaseEntity {

    private String roleCode;
    private String roleName;
    private String permissionCodes;
    private String status;
    private String remark;
}
