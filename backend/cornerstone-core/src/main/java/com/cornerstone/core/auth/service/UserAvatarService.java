package com.cornerstone.core.auth.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 本人头像能力。
 *
 * <p>只服务「改自己的头像」，不提供管理员代设：代设需要额外的机构范围校验，
 * 与用户管理页的跨机构收口同批处理，不在本接口承诺范围内。</p>
 */
public interface UserAvatarService {

    /** {@code sys_file.biz_type} 取值：头像文件绑定到用户后不会被孤儿清理回收。 */
    String BIZ_TYPE = "user_avatar";

    /** 上传并立即生效，返回新头像的 fileId；旧头像文件解绑后由孤儿清理回收。 */
    Long updateAvatar(MultipartFile file);

    /** 清除当前用户头像；本来就没有时静默返回。 */
    void clearAvatar();

    /** 账号注销时释放该用户的头像文件（不依赖登录态，供事件监听器调用）。 */
    void releaseForUser(Long userId);
}
