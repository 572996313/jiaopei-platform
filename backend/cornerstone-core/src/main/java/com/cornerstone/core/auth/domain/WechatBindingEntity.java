package com.cornerstone.core.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 微信 openid 与系统用户的绑定关系,小程序微信登录凭此换发访问令牌。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_wechat_binding")
public class WechatBindingEntity extends BaseEntity {

    @Schema(description = "微信openid,同一个小程序内唯一")
    private String openid;

    @Schema(description = "微信unionId,绑定微信开放平台后才有值")
    private String unionId;

    @Schema(description = "绑定的系统用户ID(jp_user_account.id)")
    private Long userId;

    @Schema(description = "最近一次微信登录时间")
    private LocalDateTime lastLoginAt;
}
