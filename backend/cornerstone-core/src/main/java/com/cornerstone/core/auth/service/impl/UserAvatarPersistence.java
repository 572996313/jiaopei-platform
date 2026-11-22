package com.cornerstone.core.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.service.UserAvatarService;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.file.service.FileService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 头像的持久化动作，单独成 bean 是为了让事务边界清晰：
 *
 * <p>文件上传（写对象存储 + 插 sys_file 行）必须在事务外先完成，落库失败时文件保持
 * 「未绑定」状态，可被孤儿清理回收；反过来把上传包进事务，回滚会留下桶里无主对象。</p>
 */
@Component
public class UserAvatarPersistence {

    private final UserAccountMapper userAccountMapper;

    private final FileService fileService;

    public UserAvatarPersistence(UserAccountMapper userAccountMapper, FileService fileService) {
        this.userAccountMapper = userAccountMapper;
        this.fileService = fileService;
    }

    /**
     * 切换头像：锁用户行 -> 回写新 fileId -> 绑定新文件 -> 解除旧文件绑定，全程同一事务。
     *
     * <p>锁用户行是为了串行化并发换头像：否则两个请求会各自读到同一个旧 fileId，
     * 后提交的那个把先提交的新文件变成没人引用的"已绑定"文件。</p>
     *
     * @return 被替换下来的旧头像文件ID（没有则为 null）
     */
    @Transactional
    public Long replace(Long userId, Long newFileId) {
        UserAccountEntity user = lockUser(userId);
        Long oldFileId = user.getAvatarFileId();
        if (Objects.equals(newFileId, oldFileId)) {
            return oldFileId;
        }

        LambdaUpdateWrapper<UserAccountEntity> update = new LambdaUpdateWrapper<>();
        update.eq(UserAccountEntity::getId, userId)
            .set(UserAccountEntity::getAvatarFileId, newFileId)
            .set(UserAccountEntity::getUpdatedBy, userId);
        userAccountMapper.update(null, update);

        // 新文件先绑定，保证提交前后都不会被孤儿清理误删
        fileService.bind(List.of(newFileId), UserAvatarService.BIZ_TYPE, userId);
        // 旧文件只解绑不删除：对象存储删除不可回滚，交给孤儿清理在保留时长后统一回收
        if (oldFileId != null) {
            fileService.unbind(List.of(oldFileId));
        }
        return oldFileId;
    }

    /** 清除头像：回写 NULL（updateById 会忽略 null，必须走 UpdateWrapper）并解除旧文件绑定。 */
    @Transactional
    public void clear(Long userId) {
        UserAccountEntity user = lockUser(userId);
        Long oldFileId = user.getAvatarFileId();
        if (oldFileId == null) {
            return;
        }
        LambdaUpdateWrapper<UserAccountEntity> update = new LambdaUpdateWrapper<>();
        update.eq(UserAccountEntity::getId, userId)
            .set(UserAccountEntity::getAvatarFileId, null)
            .set(UserAccountEntity::getUpdatedBy, userId);
        userAccountMapper.update(null, update);
        fileService.unbind(List.of(oldFileId));
    }

    /** 账号注销：按业务归属解除绑定，不依赖登录态（事件监听器里没有当前操作人约束）。 */
    @Transactional
    public void release(Long userId) {
        fileService.releaseBoundByBiz(UserAvatarService.BIZ_TYPE, userId);
    }

    private UserAccountEntity lockUser(Long userId) {
        UserAccountEntity user = userAccountMapper.selectOne(
            new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getId, userId)
                .last("FOR UPDATE"));
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "账号不存在或已被删除");
        }
        return user;
    }
}
