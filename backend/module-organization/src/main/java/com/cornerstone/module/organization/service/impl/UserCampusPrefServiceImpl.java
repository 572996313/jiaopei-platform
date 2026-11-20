package com.cornerstone.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.domain.entity.UserCampusPrefEntity;
import com.cornerstone.module.organization.dto.UserCampusPrefView;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.UserCampusPrefMapper;
import com.cornerstone.module.organization.service.UserCampusPrefService;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserCampusPrefServiceImpl implements UserCampusPrefService {

    private final UserCampusPrefMapper prefMapper;
    private final CampusMapper campusMapper;
    private final UserAccountMapper userAccountMapper;

    public UserCampusPrefServiceImpl(UserCampusPrefMapper prefMapper, CampusMapper campusMapper,
                                     UserAccountMapper userAccountMapper) {
        this.prefMapper = prefMapper;
        this.campusMapper = campusMapper;
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public List<UserCampusPrefView> listByCampus(Long institutionId, Long campusId) {
        if (campusId != null) {
            CampusScopeGuard.assertCampus(campusId, "校区默认账号");
        }
        LambdaQueryWrapper<UserCampusPrefEntity> wrapper = new LambdaQueryWrapper<UserCampusPrefEntity>()
            .eq(UserCampusPrefEntity::getInstitutionId, institutionId)
            .eq(campusId != null, UserCampusPrefEntity::getCampusId, campusId);
        // 未设置默认校区（NULL）不暴露具体校区信息，照常可见；已设置的按可访问校区收窄
        CampusScopeGuard.applyIncludingNull(wrapper, UserCampusPrefEntity::getCampusId);
        List<UserCampusPrefEntity> prefs = prefMapper.selectList(wrapper);
        if (prefs.isEmpty()) {
            return List.of();
        }
        Map<Long, UserAccountEntity> userById = userAccountMapper.selectBatchIds(
                prefs.stream().map(UserCampusPrefEntity::getUserId).toList()).stream()
            .collect(Collectors.toMap(UserAccountEntity::getId, Function.identity(), (left, right) -> left));
        return prefs.stream()
            .map(pref -> toView(pref.getUserId(), userById.get(pref.getUserId()), pref.getCampusId()))
            .sorted((left, right) -> nullSafeCompareLeftFirst(left.username(), right.username()))
            .toList();
    }

    @Override
    public UserCampusPrefView preference(Long institutionId, Long userId) {
        UserAccountEntity user = requireUserInInstitution(institutionId, userId);
        UserCampusPrefEntity pref = findPref(userId);
        return toView(userId, user, pref == null ? null : pref.getCampusId());
    }

    @Override
    @Transactional
    public UserCampusPrefView save(Long institutionId, Long userId, Long campusId) {
        UserAccountEntity user = requireUserInInstitution(institutionId, userId);
        assertCampusInInstitution(institutionId, campusId);

        UserCampusPrefEntity pref = findPref(userId);
        if (pref == null) {
            pref = new UserCampusPrefEntity();
            pref.setInstitutionId(institutionId);
            pref.setUserId(userId);
            pref.setCampusId(campusId);
            pref.setDeleted(Boolean.FALSE);
            try {
                prefMapper.insert(pref);
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "该用户的默认校区刚被修改,请刷新后重试");
            }
        } else {
            // updateById 会忽略 null 字段，清除默认校区必须显式 SET campus_id = NULL
            prefMapper.update(null, new LambdaUpdateWrapper<UserCampusPrefEntity>()
                .eq(UserCampusPrefEntity::getId, pref.getId())
                .set(UserCampusPrefEntity::getCampusId, campusId));
        }
        return toView(userId, user, campusId);
    }

    private UserCampusPrefEntity findPref(Long userId) {
        return prefMapper.selectOne(new LambdaQueryWrapper<UserCampusPrefEntity>()
            .eq(UserCampusPrefEntity::getUserId, userId));
    }

    /** 租户隔离:目标账号必须存在且属于操作人所在机构。 */
    private UserAccountEntity requireUserInInstitution(Long institutionId, Long userId) {
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在：" + userId);
        }
        if (!user.getInstitutionId().equals(institutionId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作其他机构的用户");
        }
        return user;
    }

    private void assertCampusInInstitution(Long institutionId, Long campusId) {
        if (campusId == null) {
            return;
        }
        CampusEntity campus = campusMapper.selectById(campusId);
        if (campus == null || !institutionId.equals(campus.getInstitutionId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "校区不属于当前机构：" + (campus == null ? campusId : campus.getCampusCode()));
        }
        // 受限角色不能把别人的默认校区指到自己无权访问的校区
        CampusScopeGuard.assertCampus(campusId, "校区默认账号");
    }

    private UserCampusPrefView toView(Long userId, UserAccountEntity user, Long campusId) {
        return new UserCampusPrefView(userId,
            user == null ? null : user.getUsername(),
            user == null ? null : user.getDisplayName(),
            campusId);
    }

    /** 账号可能已被删除(偏好行仍在),此时按空用户名排到末尾。 */
    private int nullSafeCompareLeftFirst(String left, String right) {
        if (left == null) {
            return right == null ? 0 : 1;
        }
        return right == null ? -1 : left.compareTo(right);
    }
}
