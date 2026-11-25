package com.cornerstone.core.auth.service;

import com.cornerstone.core.auth.domain.UserIdentityEntity;
import com.cornerstone.core.auth.social.ExternalIdentity;
import java.util.List;

/**
 * 第三方身份绑定关系的唯一读写入口。
 */
public interface UserIdentityService {

    /**
     * 按外部标识查绑定。先按 openId 精确查，查不到再用 unionId 兜一层
     * （同一微信在小程序/公众号/网站应用下 openId 不同，unionId 相同）。
     */
    List<UserIdentityEntity> findByExternal(String providerCode, String appType, String openId, String unionId);

    List<UserIdentityEntity> findByUser(Long userId);

    UserIdentityEntity bind(ExternalIdentity identity, Long userId);

    /** 登录成功后回写最近登录时间与昵称/头像快照 */
    void touch(ExternalIdentity identity);

    void unbind(Long userId, Long identityId);

    void unbindAllByUser(Long userId);
}
