package com.jiaopei.platform.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.auth.domain.RoleEntity;
import com.jiaopei.platform.auth.domain.UserAccountEntity;
import com.jiaopei.platform.auth.domain.UserRoleRelEntity;
import com.jiaopei.platform.auth.dto.ResetPasswordResponse;
import com.jiaopei.platform.auth.dto.UserCreateRequest;
import com.jiaopei.platform.auth.dto.UserRoleUpdateRequest;
import com.jiaopei.platform.auth.dto.UserUpdateRequest;
import com.jiaopei.platform.auth.dto.UserView;
import com.jiaopei.platform.auth.mapper.RoleMapper;
import com.jiaopei.platform.auth.mapper.UserAccountMapper;
import com.jiaopei.platform.auth.mapper.UserRoleRelMapper;
import com.jiaopei.platform.auth.service.AuthApplicationService;
import com.jiaopei.platform.auth.service.SystemUserService;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.domain.entity.InstitutionEntity;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemUserServiceImpl implements SystemUserService {

    private static final String ACTIVE = "active";
    private static final String DEFAULT_INITIAL_PASSWORD = "Init@123456";

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final InstitutionMapper institutionMapper;
    private final CampusMapper campusMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthApplicationService authApplicationService;

    public SystemUserServiceImpl(UserAccountMapper userAccountMapper, RoleMapper roleMapper,
                                 UserRoleRelMapper userRoleRelMapper, InstitutionMapper institutionMapper,
                                 CampusMapper campusMapper, PasswordEncoder passwordEncoder,
                                 AuthApplicationService authApplicationService) {
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.institutionMapper = institutionMapper;
        this.campusMapper = campusMapper;
        this.passwordEncoder = passwordEncoder;
        this.authApplicationService = authApplicationService;
    }

    @Override
    public List<UserView> listUsers() {
        return userAccountMapper.selectList(new LambdaQueryWrapper<UserAccountEntity>()
                .orderByAsc(UserAccountEntity::getUsername))
            .stream()
            .map(this::toView)
            .toList();
    }

    @Override
    @Transactional
    public UserView createUser(UserCreateRequest request) {
        Long institutionId = request.institutionId();
        assertInstitutionExists(institutionId);
        assertCampusBelongsToInstitution(request.defaultCampusId(), institutionId);
        assertUniqueUsername(normalize(request.username()));

        UserAccountEntity entity = new UserAccountEntity();
        entity.setInstitutionId(institutionId);
        entity.setDefaultCampusId(request.defaultCampusId());
        entity.setUsername(normalize(request.username()));
        entity.setPasswordHash(passwordEncoder.encode(initialPassword(request.initialPassword())));
        entity.setDisplayName(normalize(request.displayName()));
        entity.setPhone(normalize(request.phone()));
        entity.setStatus(defaultStatus(request.status()));
        entity.setPasswordResetRequired(Boolean.TRUE);
        entity.setForceChangePassword(Boolean.TRUE);
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            userAccountMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名已存在：" + entity.getUsername());
        }
        return toView(entity);
    }

    @Override
    @Transactional
    public UserView updateUser(Long id, UserUpdateRequest request) {
        UserAccountEntity entity = requireUser(id);
        assertInstitutionExists(request.institutionId());
        assertCampusBelongsToInstitution(request.defaultCampusId(), request.institutionId());

        String oldStatus = entity.getStatus();

        entity.setInstitutionId(request.institutionId());
        entity.setDefaultCampusId(request.defaultCampusId());
        entity.setDisplayName(normalize(request.displayName()));
        entity.setPhone(normalize(request.phone()));
        entity.setStatus(normalize(request.status()));
        entity.setRemark(request.remark());
        userAccountMapper.updateById(entity);

        if (ACTIVE.equals(oldStatus) && !ACTIVE.equals(entity.getStatus())) {
            authApplicationService.invalidateUserSessions(id);
        }

        return toView(entity);
    }

    @Override
    @Transactional
    public ResetPasswordResponse resetPassword(Long id) {
        UserAccountEntity entity = requireUser(id);
        entity.setPasswordHash(passwordEncoder.encode(DEFAULT_INITIAL_PASSWORD));
        entity.setPasswordResetRequired(Boolean.TRUE);
        entity.setForceChangePassword(Boolean.TRUE);
        entity.setPasswordUpdatedAt(null);
        userAccountMapper.updateById(entity);

        authApplicationService.invalidateUserSessions(id);

        return new ResetPasswordResponse(entity.getId(), DEFAULT_INITIAL_PASSWORD, Boolean.TRUE);
    }

    @Override
    @Transactional
    public UserView updateRoles(Long id, UserRoleUpdateRequest request) {
        UserAccountEntity user = requireUser(id);
        Set<Long> roleIds = new LinkedHashSet<>(request.roleIds() == null ? List.of() : request.roleIds());
        validateRoles(roleIds);

        userRoleRelMapper.physicalDeleteByUserId(id);
        for (Long roleId : roleIds) {
            UserRoleRelEntity relation = new UserRoleRelEntity();
            relation.setUserId(id);
            relation.setRoleId(roleId);
            relation.setDeleted(Boolean.FALSE);
            userRoleRelMapper.insert(relation);
        }
        return toView(user);
    }

    private UserView toView(UserAccountEntity entity) {
        List<UserRoleRelEntity> relations = userRoleRelMapper.selectList(new LambdaQueryWrapper<UserRoleRelEntity>()
            .eq(UserRoleRelEntity::getUserId, entity.getId()));
        List<Long> roleIds = relations.stream()
            .map(UserRoleRelEntity::getRoleId)
            .toList();
        List<String> roleCodes = roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds).stream()
            .map(RoleEntity::getRoleCode)
            .toList();
        return new UserView(
            entity.getId(),
            entity.getInstitutionId(),
            entity.getDefaultCampusId(),
            entity.getUsername(),
            entity.getDisplayName(),
            entity.getPhone(),
            entity.getStatus(),
            entity.getPasswordResetRequired(),
            entity.getLastLoginAt(),
            entity.getRemark(),
            roleIds,
            roleCodes
        );
    }

    private UserAccountEntity requireUser(Long id) {
        UserAccountEntity entity = userAccountMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在：" + id);
        }
        return entity;
    }

    private void assertUniqueUsername(String username) {
        Long count = userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccountEntity>()
            .eq(UserAccountEntity::getUsername, username));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名已存在：" + username);
        }
    }

    private void assertInstitutionExists(Long institutionId) {
        InstitutionEntity institution = institutionMapper.selectById(institutionId);
        if (institution == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "机构不存在：" + institutionId);
        }
    }

    private void assertCampusBelongsToInstitution(Long campusId, Long institutionId) {
        if (campusId == null) {
            return;
        }
        CampusEntity campus = campusMapper.selectById(campusId);
        if (campus == null || !institutionId.equals(campus.getInstitutionId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "默认校区不属于该机构");
        }
    }

    private void validateRoles(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<RoleEntity> roles = roleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "部分角色不存在");
        }
    }

    private String initialPassword(String requestedPassword) {
        String normalized = normalize(requestedPassword);
        return normalized == null || normalized.isBlank() ? DEFAULT_INITIAL_PASSWORD : normalized;
    }

    private String defaultStatus(String status) {
        String normalized = normalize(status);
        return normalized == null || normalized.isBlank() ? ACTIVE : normalized;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
