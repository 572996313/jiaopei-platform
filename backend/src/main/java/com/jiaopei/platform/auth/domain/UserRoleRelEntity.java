package com.jiaopei.platform.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_user_role_rel")
public class UserRoleRelEntity extends BaseEntity {

    private Long userId;
    private Long roleId;
}
