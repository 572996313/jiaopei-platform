package com.cornerstone.core.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
import com.cornerstone.core.auth.dto.ResetPasswordResponse;
import com.cornerstone.core.auth.dto.UserCreateRequest;
import com.cornerstone.core.auth.dto.UserPageQuery;
import com.cornerstone.core.auth.dto.UserRoleUpdateRequest;
import com.cornerstone.core.auth.dto.UserUpdateRequest;
import com.cornerstone.core.auth.dto.UserView;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.auth.service.CampusBelongingValidator;
import com.cornerstone.core.auth.service.SystemUserService;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<CampusBelongingValidator> campusBelongingValidator;
    private final PasswordEncoder passwordEncoder;
    private final AuthApplicationService authApplicationService;

    public SystemUserServiceImpl(UserAccountMapper userAccountMapper, RoleMapper roleMapper,
                                 UserRoleRelMapper userRoleRelMapper, InstitutionMapper institutionMapper,
                                 ObjectProvider<CampusBelongingValidator> campusBelongingValidator,
                                 PasswordEncoder passwordEncoder,
                                 AuthApplicationService authApplicationService) {
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.institutionMapper = institutionMapper;
        this.campusBelongingValidator = campusBelongingValidator;
        this.passwordEncoder = passwordEncoder;
        this.authApplicationService = authApplicationService;
    }

    @Override
    public List<UserView> listUsers() {
        return toViews(userAccountMapper.selectList(new LambdaQueryWrapper<UserAccountEntity>()
            .orderByAsc(UserAccountEntity::getUsername)));
    }

    @Override
    public PageResult<UserView> pageUsers(UserPageQuery query) {
        LambdaQueryWrapper<UserAccountEntity> wrapper = new LambdaQueryWrapper<>();
        String keyword = normalize(query.getKeyword());
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(UserAccountEntity::getUsername, keyword)
                .or().like(UserAccountEntity::getDisplayName, keyword)
                .or().like(UserAccountEntity::getPhone, keyword));
        }
        String status = normalize(query.getStatus());
        if (status != null && !status.isBlank()) {
            wrapper.eq(UserAccountEntity::getStatus, status);
        }
        if (query.getInstitutionId() != null) {
            wrapper.eq(UserAccountEntity::getInstitutionId, query.getInstitutionId());
        }
        wrapper.orderByAsc(UserAccountEntity::getUsername);

        Page<UserAccountEntity> page = userAccountMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, toViews(page.getRecords()));
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
        return toView(entity, roleIds, roleCodes);
    }

    /**
     * 批量组装用户视图:两次查询取回页内全部用户的角色关联与角色编码,
     * 避免逐用户查询角色的 N+1 问题;list 与 page 共用。
     */
    private List<UserView> toViews(List<UserAccountEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = entities.stream().map(UserAccountEntity::getId).toList();
        Map<Long, List<Long>> roleIdsByUser = userRoleRelMapper.selectList(new LambdaQueryWrapper<UserRoleRelEntity>()
                .in(UserRoleRelEntity::getUserId, userIds))
            .stream()
            .collect(Collectors.groupingBy(UserRoleRelEntity::getUserId,
                Collectors.mapping(UserRoleRelEntity::getRoleId, Collectors.toList())));
        Set<Long> allRoleIds = roleIdsByUser.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toSet());
        Map<Long, String> roleCodeById = allRoleIds.isEmpty() ? Map.of()
            : roleMapper.selectBatchIds(allRoleIds).stream()
                .collect(Collectors.toMap(RoleEntity::getId, RoleEntity::getRoleCode, (left, right) -> left));
        return entities.stream()
            .map(entity -> {
                List<Long> roleIds = roleIdsByUser.getOrDefault(entity.getId(), List.of());
                List<String> roleCodes = roleIds.stream()
                    .map(roleCodeById::get)
                    .filter(Objects::nonNull)
                    .toList();
                return toView(entity, roleIds, roleCodes);
            })
            .toList();
    }

    private UserView toView(UserAccountEntity entity, List<Long> roleIds, List<String> roleCodes) {
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
        // 校区概念由业务模块提供实现；未挂载时（纯底座项目）无校验器，跳过
        CampusBelongingValidator validator = campusBelongingValidator.getIfAvailable();
        if (validator != null) {
            validator.assertCampusBelongsToInstitution(campusId, institutionId);
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
