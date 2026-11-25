package com.cornerstone.core.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
import com.cornerstone.core.auth.dto.ResetPasswordResponse;
import com.cornerstone.core.auth.dto.UserCreateRequest;
import com.cornerstone.core.auth.dto.UserPageQuery;
import com.cornerstone.core.auth.dto.UserRoleUpdateRequest;
import com.cornerstone.core.auth.dto.UserUpdateRequest;
import com.cornerstone.core.auth.dto.UserView;
import com.cornerstone.core.auth.event.UserAccountDeregisteredEvent;
import com.cornerstone.core.auth.mapper.AuthInvitationMapper;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.service.AuthApplicationService;
import com.cornerstone.core.auth.service.SystemUserService;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.PageSortSupport;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemUserServiceImpl implements SystemUserService {

    private static final String ACTIVE = "active";
    private static final String DEFAULT_INITIAL_PASSWORD = "Init@123456";
    /** 平台管理员角色编码:注销账号时用它判断"是否最后一个能管系统的人" */
    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    /** 用户列表可排序字段:与列表列一一对应,机构/校区是 ID 列不参与排序 */
    private static final Map<String, SFunction<UserAccountEntity, ?>> USER_SORTABLE = Map.of(
        "username", UserAccountEntity::getUsername,
        "displayName", UserAccountEntity::getDisplayName,
        "phone", UserAccountEntity::getPhone,
        "status", UserAccountEntity::getStatus,
        "passwordResetRequired", UserAccountEntity::getPasswordResetRequired,
        "lastLoginAt", UserAccountEntity::getLastLoginAt,
        "remark", UserAccountEntity::getRemark);

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final InstitutionMapper institutionMapper;
    private final AuthInvitationMapper invitationMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthApplicationService authApplicationService;
    private final ApplicationEventPublisher eventPublisher;

    public SystemUserServiceImpl(UserAccountMapper userAccountMapper, RoleMapper roleMapper,
                                 UserRoleRelMapper userRoleRelMapper, InstitutionMapper institutionMapper,
                                 AuthInvitationMapper invitationMapper,
                                 PasswordEncoder passwordEncoder,
                                 AuthApplicationService authApplicationService,
                                 ApplicationEventPublisher eventPublisher) {
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.institutionMapper = institutionMapper;
        this.invitationMapper = invitationMapper;
        this.passwordEncoder = passwordEncoder;
        this.authApplicationService = authApplicationService;
        this.eventPublisher = eventPublisher;
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
        if (!PageSortSupport.apply(wrapper, query, USER_SORTABLE, UserAccountEntity::getId)) {
            wrapper.orderByAsc(UserAccountEntity::getUsername);
        }

        Page<UserAccountEntity> page = userAccountMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, toViews(page.getRecords()));
    }

    @Override
    @Transactional
    public UserView createUser(UserCreateRequest request) {
        Long institutionId = request.institutionId();
        assertInstitutionExists(institutionId);
        assertUniqueUsername(normalize(request.username()));

        UserAccountEntity entity = new UserAccountEntity();
        entity.setInstitutionId(institutionId);
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

        String oldStatus = entity.getStatus();

        entity.setInstitutionId(request.institutionId());
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
        // 角色快照写进了 JWT，改完必须让该用户重新登录，否则旧权限在令牌过期前继续可用
        authApplicationService.invalidateUserSessions(id);
        return toView(user);
    }

    @Override
    @Transactional
    public void deregisterUser(Long id) {
        AuthenticatedUser current = AuthContext.requireUser();
        UserAccountEntity entity = requireUser(id);

        if (Objects.equals(current.userId(), id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能注销当前登录的账号");
        }
        assertSameInstitutionUnlessPlatformAdmin(current, entity);
        if (ACTIVE.equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "账号「" + entity.getUsername() + "」还在启用中，请先停用再注销");
        }
        assertNotLastActivePlatformAdmin(entity);

        // 先清会话再删行:账号行没了,残留会话里的权限快照就没人能复核了
        authApplicationService.invalidateUserSessions(id);
        // 激活码绑定的是这个账号,FK 指向 sys_user_account,不先清掉删账号会被 1451 挡住
        invitationMapper.physicalDeleteByUserId(id);
        userRoleRelMapper.physicalDeleteByUserId(id);
        // 业务域(学员/教师档案归属、校区偏好)由各自监听器清理;第三方身份绑定由底座监听器清理。
        // 同事务、任一失败整体回滚，且必须发生在物理删账号之前
        eventPublisher.publishEvent(new UserAccountDeregisteredEvent(id));
        // 物理删而非逻辑删:username 唯一键含 deleted,软删行会永久占住这个账号名
        userAccountMapper.physicalDeleteById(id);
    }

    /**
     * 机构比对:注销是最不可逆的写操作,非平台管理员只能动本机构的账号。
     * 平台管理员保持现状跨机构操作(用户管理页目前只对平台角色开放)。
     */
    private void assertSameInstitutionUnlessPlatformAdmin(AuthenticatedUser current, UserAccountEntity target) {
        if (current.roles() != null && current.roles().contains(PLATFORM_ADMIN_ROLE)) {
            return;
        }
        if (!Objects.equals(current.orgId(), target.getInstitutionId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能注销本机构的账号");
        }
    }

    /** 挡住"注销掉最后一个能管系统的人"——系统会没人能进用户管理页，只能进数据库改。 */
    private void assertNotLastActivePlatformAdmin(UserAccountEntity target) {
        RoleEntity platformRole = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
            .eq(RoleEntity::getRoleCode, PLATFORM_ADMIN_ROLE));
        if (platformRole == null) {
            return;
        }
        Long holds = userRoleRelMapper.selectCount(new LambdaQueryWrapper<UserRoleRelEntity>()
            .eq(UserRoleRelEntity::getUserId, target.getId())
            .eq(UserRoleRelEntity::getRoleId, platformRole.getId()));
        if (holds == null || holds == 0) {
            return;
        }
        List<Long> otherUserIds = userRoleRelMapper.selectList(new LambdaQueryWrapper<UserRoleRelEntity>()
                .eq(UserRoleRelEntity::getRoleId, platformRole.getId())
                .ne(UserRoleRelEntity::getUserId, target.getId()))
            .stream()
            .map(UserRoleRelEntity::getUserId)
            .toList();
        Long activeOthers = otherUserIds.isEmpty() ? 0L : userAccountMapper.selectCount(
            new LambdaQueryWrapper<UserAccountEntity>()
                .in(UserAccountEntity::getId, otherUserIds)
                .eq(UserAccountEntity::getStatus, ACTIVE));
        if (activeOthers == null || activeOthers == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该账号是最后一个可用的平台管理员，注销后将无人能管理系统，请先给其他账号分配平台管理员角色");
        }
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
