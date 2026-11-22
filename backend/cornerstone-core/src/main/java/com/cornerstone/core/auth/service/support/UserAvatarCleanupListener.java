package com.cornerstone.core.auth.service.support;

import com.cornerstone.core.auth.event.UserAccountDeregisteredEvent;
import com.cornerstone.core.auth.service.UserAvatarService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号注销时释放头像文件绑定。
 *
 * <p>账号行是物理删除，{@code avatar_file_id} 会随行消失；若文件仍绑在
 * {@code (user_avatar, userId)} 上，孤儿清理永远碰不到它（它不算"未绑定"），
 * 所以必须在删账号前解除绑定，让清理任务能回收对象。</p>
 */
@Component
public class UserAvatarCleanupListener {

    private final UserAvatarService userAvatarService;

    public UserAvatarCleanupListener(UserAvatarService userAvatarService) {
        this.userAvatarService = userAvatarService;
    }

    @EventListener
    public void onUserAccountDeregistered(UserAccountDeregisteredEvent event) {
        userAvatarService.releaseForUser(event.userId());
    }
}
