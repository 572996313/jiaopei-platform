package com.jiaopei.platform.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_user_token")
public class UserTokenEntity extends BaseEntity {

    private Long userId;
    private String accessToken;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
}
