package com.jiaopei.platform.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_user_account")
public class UserAccountEntity extends BaseEntity {

    private Long institutionId;
    private Long defaultCampusId;
    private String username;
    private String passwordHash;
    private String displayName;
    private String phone;
    private String status;
    private Boolean passwordResetRequired;
    private LocalDateTime passwordUpdatedAt;
    private Boolean forceChangePassword;
    private LocalDateTime lastLoginAt;
    private String remark;
}
