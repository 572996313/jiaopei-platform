package com.cornerstone.core.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "注册来源(ADMIN=后台创建,WECHAT_MINIAPP=小程序自助注册)",
            allowableValues = {"ADMIN", "WECHAT_MINIAPP"})
    private String registerSource;

    @Schema(description = "备注")
    private String remark;
}
