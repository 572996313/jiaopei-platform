package com.cornerstone.core.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cornerstone.core.auth.domain.UserIdentityEntity;
import com.cornerstone.core.auth.event.UserAccountDeregisteredEvent;
import com.cornerstone.core.auth.mapper.UserIdentityMapper;
import com.cornerstone.core.auth.service.UserIdentityService;
import com.cornerstone.core.auth.social.ExternalIdentity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserIdentityServiceImpl implements UserIdentityService {

    private final UserIdentityMapper userIdentityMapper;

    public UserIdentityServiceImpl(UserIdentityMapper userIdentityMapper) {
        this.userIdentityMapper = userIdentityMapper;
    }

    @Override
    public List<UserIdentityEntity> findByExternal(String providerCode, String appType, String openId,
                                                   String unionId) {
        List<UserIdentityEntity> byOpenId = userIdentityMapper.selectList(
            new LambdaQueryWrapper<UserIdentityEntity>()
                .eq(UserIdentityEntity::getProviderCode, providerCode)
                .eq(UserIdentityEntity::getAppType, appType)
                .eq(UserIdentityEntity::getOpenId, openId)
                .orderByAsc(UserIdentityEntity::getId));
        if (!byOpenId.isEmpty() || unionId == null || unionId.isBlank()) {
            return byOpenId;
        }
        return userIdentityMapper.selectList(new LambdaQueryWrapper<UserIdentityEntity>()
            .eq(UserIdentityEntity::getProviderCode, providerCode)
            .eq(UserIdentityEntity::getUnionId, unionId)
            .orderByAsc(UserIdentityEntity::getId));
    }

    @Override
    public List<UserIdentityEntity> findByUser(Long userId) {
        return userIdentityMapper.selectList(new LambdaQueryWrapper<UserIdentityEntity>()
            .eq(UserIdentityEntity::getUserId, userId)
            .orderByAsc(UserIdentityEntity::getId));
    }

    @Override
    @Transactional
    public UserIdentityEntity bind(ExternalIdentity identity, Long userId) {
        UserIdentityEntity existing = findBound(identity, userId);
        if (existing != null) {
            // 幂等：同一外部身份重复绑定同一账号时只刷新资料快照
            LambdaUpdateWrapper<UserIdentityEntity> wrapper = new LambdaUpdateWrapper<UserIdentityEntity>()
                .eq(UserIdentityEntity::getId, existing.getId());
            applySnapshot(wrapper, identity);
            userIdentityMapper.update(null, wrapper);
            return userIdentityMapper.selectById(existing.getId());
        }
        UserIdentityEntity created = new UserIdentityEntity();
        created.setProviderCode(identity.providerCode());
        created.setAppType(identity.appType());
        created.setOpenId(identity.openId());
        created.setUnionId(identity.unionId());
        created.setUserId(userId);
        created.setNickname(identity.nickname());
        created.setAvatarUrl(identity.avatarUrl());
        try {
            userIdentityMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            // 并发绑定同一外部身份：先查后插的竞态由唯一键兜底，撞键说明另一个请求已插入
            UserIdentityEntity concurrent = findBound(identity, userId);
            if (concurrent == null) {
                throw e;
            }
            return concurrent;
        }
    }

    @Override
    public void touch(ExternalIdentity identity) {
        LambdaUpdateWrapper<UserIdentityEntity> wrapper = new LambdaUpdateWrapper<UserIdentityEntity>()
            .eq(UserIdentityEntity::getProviderCode, identity.providerCode())
            .eq(UserIdentityEntity::getAppType, identity.appType())
            .eq(UserIdentityEntity::getOpenId, identity.openId())
            .set(UserIdentityEntity::getLastLoginAt, LocalDateTime.now());
        // 小程序 code2Session 不返回昵称/头像，null 不能覆盖注册时用户填的昵称
        applySnapshot(wrapper, identity);
        userIdentityMapper.update(null, wrapper);
    }

    @Override
    public void unbind(Long userId, Long identityId) {
        userIdentityMapper.physicalDeleteByIdAndUserId(identityId, userId);
    }

    @Override
    public void unbindAllByUser(Long userId) {
        userIdentityMapper.physicalDeleteByUserId(userId);
    }

    /**
     * 账号注销时清掉本账号的全部第三方身份。身份表归底座所有，
     * 清理放在底座监听器里，不依赖业务模块是否装配；与注销同事务，
     * 必须在物理删账号之前完成，否则外键会把删除拦成 1451。
     */
    @EventListener
    public void onUserAccountDeregistered(UserAccountDeregisteredEvent event) {
        unbindAllByUser(event.userId());
    }

    private UserIdentityEntity findBound(ExternalIdentity identity, Long userId) {
        return userIdentityMapper.selectOne(new LambdaQueryWrapper<UserIdentityEntity>()
            .eq(UserIdentityEntity::getProviderCode, identity.providerCode())
            .eq(UserIdentityEntity::getAppType, identity.appType())
            .eq(UserIdentityEntity::getOpenId, identity.openId())
            .eq(UserIdentityEntity::getUserId, userId));
    }

    private void applySnapshot(LambdaUpdateWrapper<UserIdentityEntity> wrapper, ExternalIdentity identity) {
        if (identity.nickname() != null) {
            wrapper.set(UserIdentityEntity::getNickname, identity.nickname());
        }
        if (identity.avatarUrl() != null) {
            wrapper.set(UserIdentityEntity::getAvatarUrl, identity.avatarUrl());
        }
        if (identity.unionId() != null) {
            wrapper.set(UserIdentityEntity::getUnionId, identity.unionId());
        }
    }
}
