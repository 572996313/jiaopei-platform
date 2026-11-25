package com.cornerstone.core.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 第三方身份与系统账号的绑定关系。
 *
 * <p>同一外部身份允许绑定多个账号（例如同一个微信既是家长又是员工），
 * 登录时由前端选择账号；解绑走物理删除，避免墓碑占住“解绑后重新绑定”的唯一键。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_identity")
@Schema(description = "第三方身份绑定关系")
public class UserIdentityEntity extends BaseEntity {

    @Schema(description = "渠道编码(如 wechat/linuxdo)")
    private String providerCode;

    @Schema(description = "应用类型(miniapp=小程序,mp=公众号,website=网站应用)")
    private String appType;

    @Schema(description = "渠道内唯一用户标识(微信openid/LinuxDo用户ID)")
    private String openId;

    @Schema(description = "微信unionId(绑定开放平台后才有值)")
    private String unionId;

    @Schema(description = "绑定的系统账号ID(sys_user_account.id)")
    private Long userId;

    @Schema(description = "外部昵称快照")
    private String nickname;

    @Schema(description = "外部头像地址快照")
    private String avatarUrl;

    @Schema(description = "最近一次通过该身份登录时间")
    private LocalDateTime lastLoginAt;
}
