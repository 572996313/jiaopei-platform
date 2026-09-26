package com.cornerstone.core.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.auth.domain.AuthInvitationEntity;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
import com.cornerstone.core.auth.dto.AccountActivateRequest;
import com.cornerstone.core.auth.dto.ActivationCodeCreateRequest;
import com.cornerstone.core.auth.dto.InvitationAccountSpec;
import com.cornerstone.core.auth.dto.InvitationCreateRequest;
import com.cornerstone.core.auth.dto.InvitationPageQuery;
import com.cornerstone.core.auth.dto.InvitationPrecheckView;
import com.cornerstone.core.auth.dto.InvitationRegisterRequest;
import com.cornerstone.core.auth.dto.InvitationView;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.event.UserRegisteredEvent;
import com.cornerstone.core.auth.mapper.AuthInvitationMapper;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.security.UserAccessTokenService;
import com.cornerstone.core.auth.service.AuthInvitationService;
import com.cornerstone.core.auth.service.PasswordValidator;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.PageSortSupport;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
public class AuthInvitationServiceImpl implements AuthInvitationService {

    private static final String ACTIVE = "active";
    private static final int DEFAULT_ACTIVATION_VALID_DAYS = 7;

    /** 邀请码/激活码列表可排序字段 */
    private static final Map<String, SFunction<AuthInvitationEntity, ?>> INVITATION_SORTABLE = Map.of(
        "code", AuthInvitationEntity::getCode,
        "kind", AuthInvitationEntity::getKind,
        "status", AuthInvitationEntity::getStatus,
        "maxUses", AuthInvitationEntity::getMaxUses,
        "usedCount", AuthInvitationEntity::getUsedCount,
        "expiresAt", AuthInvitationEntity::getExpiresAt,
        "remark", AuthInvitationEntity::getRemark,
        "createdAt", AuthInvitationEntity::getCreatedAt);
    /** 去掉了易混字符(0/O/1/I)的大写字母数字表 */
    private static final char[] CODE_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthInvitationMapper invitationMapper;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final InstitutionMapper institutionMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final UserAccessTokenService userAccessTokenService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthInvitationServiceImpl(AuthInvitationMapper invitationMapper,
                                     UserAccountMapper userAccountMapper, RoleMapper roleMapper,
                                     UserRoleRelMapper userRoleRelMapper, InstitutionMapper institutionMapper,
                                     PasswordEncoder passwordEncoder, PasswordValidator passwordValidator,
                                     UserAccessTokenService userAccessTokenService,
                                     ApplicationEventPublisher eventPublisher) {
        this.invitationMapper = invitationMapper;
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.institutionMapper = institutionMapper;
        this.passwordEncoder = passwordEncoder;
        this.passwordValidator = passwordValidator;
        this.userAccessTokenService = userAccessTokenService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public InvitationView createInvite(InvitationCreateRequest request) {
        assertInstitutionExists(request.institutionId());
        RoleEntity role = requireActiveRole(request.roleCode());

        AuthInvitationEntity entity = new AuthInvitationEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setKind(KIND_INVITE);
        entity.setRoleCode(role.getRoleCode());
        entity.setMaxUses(request.maxUses());
        entity.setUsedCount(0);
        entity.setExpiresAt(expiresAt(request.validDays()));
        entity.setStatus(ACTIVE);
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        insertWithGeneratedCode(entity, "INV");
        return toView(entity);
    }

    @Override
    @Transactional
    public InvitationView createActivationCode(ActivationCodeCreateRequest request) {
        UserAccountEntity user = userAccountMapper.selectById(request.userId());
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "账号不存在：" + request.userId());
        }
        if (!ACTIVE.equals(user.getStatus()) && !STATUS_PENDING_ACTIVATION.equals(user.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "账号已被停用,不能生成激活码");
        }
        // 幂等:同一待激活账号重复申请时返回仍在有效期内的激活码,不另发新码
        if (STATUS_PENDING_ACTIVATION.equals(user.getStatus())) {
            AuthInvitationEntity existing = findReusableActivation(user.getId());
            if (existing != null) {
                return toView(existing);
            }
        }

        AuthInvitationEntity entity = new AuthInvitationEntity();
        entity.setInstitutionId(user.getInstitutionId());
        entity.setKind(KIND_ACTIVATE);
        entity.setUserId(user.getId());
        entity.setMaxUses(1);
        entity.setUsedCount(0);
        int validDays = request.validDays() == null ? DEFAULT_ACTIVATION_VALID_DAYS : request.validDays();
        entity.setExpiresAt(expiresAt(validDays));
        entity.setStatus(ACTIVE);
        entity.setDeleted(Boolean.FALSE);
        insertWithGeneratedCode(entity, "ACT");
        return toView(entity);
    }

    @Override
    public PageResult<InvitationView> pageInvitations(InvitationPageQuery query) {
        LambdaQueryWrapper<AuthInvitationEntity> wrapper = new LambdaQueryWrapper<>();
        String keyword = normalize(query.getKeyword());
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AuthInvitationEntity::getCode, keyword)
                .or().like(AuthInvitationEntity::getRemark, keyword));
        }
        eqIfPresent(wrapper, AuthInvitationEntity::getKind, query.getKind());
        eqIfPresent(wrapper, AuthInvitationEntity::getStatus, query.getStatus());
        if (query.getInstitutionId() != null) {
            wrapper.eq(AuthInvitationEntity::getInstitutionId, query.getInstitutionId());
        }
        if (!PageSortSupport.apply(wrapper, query, INVITATION_SORTABLE, AuthInvitationEntity::getId)) {
            wrapper.orderByDesc(AuthInvitationEntity::getCreatedAt);
        }

        Page<AuthInvitationEntity> page = invitationMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, toViews(page.getRecords()));
    }

    @Override
    @Transactional
    public void disable(Long id) {
        AuthInvitationEntity entity = invitationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "凭证不存在：" + id);
        }
        if (!ACTIVE.equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "凭证已处于停用状态");
        }
        entity.setStatus("disabled");
        invitationMapper.updateById(entity);
    }

    @Override
    public InvitationPrecheckView precheck(String code) {
        AuthInvitationEntity invitation = findByCode(code);
        if (invitation == null) {
            return invalid("凭证不存在");
        }
        String problem = checkUsable(invitation);
        if (problem != null) {
            return invalid(problem);
        }
        InstitutionEntity institution = institutionMapper.selectById(invitation.getInstitutionId());
        String roleName = null;
        if (invitation.getRoleCode() != null) {
            RoleEntity role = findActiveRole(invitation.getRoleCode());
            roleName = role == null ? null : role.getRoleName();
        }
        return new InvitationPrecheckView(true, invitation.getKind(),
            institution == null ? null : institution.getOrgName(),
            invitation.getRoleCode(), roleName, invitation.getExpiresAt(), null);
    }

    @Override
    @Transactional
    public LoginResponse register(InvitationRegisterRequest request) {
        AuthInvitationEntity invitation = requireUsable(request.code(), KIND_INVITE);
        UserAccountEntity user = createInvitedAccount(invitation, request.username(),
            request.displayName(), request.phone(), request.password(), REGISTER_SOURCE_INVITATION);
        return userAccessTokenService.issue(user);
    }

    @Override
    @Transactional
    public UserAccountEntity registerInvitedAccount(InvitationAccountSpec spec) {
        AuthInvitationEntity invitation = requireUsable(spec.code(), KIND_INVITE);
        // 无密码建号:不校验密码策略,passwordHash 留空;账号来源由业务指定(如微信端)
        String source = normalize(spec.registerSource());
        return createInvitedAccount(invitation, spec.username(), spec.displayName(), spec.phone(),
            null, source == null || source.isBlank() ? REGISTER_SOURCE_INVITATION : source);
    }

    /**
     * 依据可用的 INVITE 凭证建号、绑角色、核销凭证并发布注册事件。
     * rawPassword 为 null 时建成无密码账号(passwordUpdatedAt 同样留空),供业务身份兑换使用;
     * 非 null 时按系统密码策略校验。机构与角色一律取凭证固定值。
     */
    private UserAccountEntity createInvitedAccount(AuthInvitationEntity invitation, String rawUsername,
                                                   String displayName, String phone,
                                                   String rawPassword, String registerSource) {
        String username = normalize(rawUsername);
        if (username == null || username.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名不能为空");
        }
        if (rawPassword != null) {
            passwordValidator.validate(rawPassword);
        }
        assertUniqueUsername(username);
        RoleEntity role = requireActiveRole(invitation.getRoleCode());
        consume(invitation);

        UserAccountEntity user = new UserAccountEntity();
        user.setInstitutionId(invitation.getInstitutionId());
        user.setUsername(username);
        if (rawPassword != null) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setPasswordUpdatedAt(LocalDateTime.now());
        }
        user.setDisplayName(normalize(displayName));
        user.setPhone(normalize(phone));
        user.setStatus(ACTIVE);
        user.setPasswordResetRequired(Boolean.FALSE);
        user.setForceChangePassword(Boolean.FALSE);
        user.setRegisterSource(registerSource);
        user.setDeleted(Boolean.FALSE);
        try {
            userAccountMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名已存在：" + username);
        }

        UserRoleRelEntity relation = new UserRoleRelEntity();
        relation.setUserId(user.getId());
        relation.setRoleId(role.getId());
        relation.setDeleted(Boolean.FALSE);
        userRoleRelMapper.insert(relation);

        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), user.getInstitutionId(),
            KIND_INVITE, role.getRoleCode(), invitation.getId()));
        return user;
    }

    @Override
    @Transactional
    public LoginResponse activate(AccountActivateRequest request) {
        AuthInvitationEntity invitation = requireUsable(request.code(), KIND_ACTIVATE);
        UserAccountEntity user = userAccountMapper.selectById(invitation.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "凭证关联的账号不存在");
        }
        if (ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该账号已激活,请直接登录");
        }
        if (!STATUS_PENDING_ACTIVATION.equals(user.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该账号状态不支持激活");
        }
        passwordValidator.validate(request.password());
        consume(invitation);

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(ACTIVE);
        user.setPasswordResetRequired(Boolean.FALSE);
        user.setForceChangePassword(Boolean.FALSE);
        user.setPasswordUpdatedAt(LocalDateTime.now());
        userAccountMapper.updateById(user);

        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), user.getInstitutionId(),
            KIND_ACTIVATE, null, invitation.getId()));
        return userAccessTokenService.issue(user);
    }

    // ------------------------------------------------------------------ 内部方法

    private AuthInvitationEntity requireUsable(String code, String expectedKind) {
        AuthInvitationEntity invitation = findByCode(code);
        if (invitation == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "凭证不存在");
        }
        if (!expectedKind.equals(invitation.getKind())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                KIND_INVITE.equals(expectedKind) ? "该凭证是激活码,请在激活页使用" : "该凭证是邀请码,请在注册页使用");
        }
        String problem = checkUsable(invitation);
        if (problem != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, problem);
        }
        return invitation;
    }

    /** 返回 null 表示凭证可用,否则为不可用原因。 */
    private String checkUsable(AuthInvitationEntity invitation) {
        if (!ACTIVE.equals(invitation.getStatus())) {
            return "凭证已停用";
        }
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            return "凭证已过期";
        }
        if (invitation.getMaxUses() != null && invitation.getUsedCount() != null
            && invitation.getUsedCount() >= invitation.getMaxUses()) {
            return "凭证使用次数已用尽";
        }
        return null;
    }

    /** 条件 UPDATE 核销,并发下只有一个请求能扣减成功。 */
    private void consume(AuthInvitationEntity invitation) {
        if (invitationMapper.tryConsume(invitation.getId()) == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "凭证已失效或使用次数已用尽");
        }
    }

    private AuthInvitationEntity findByCode(String code) {
        String normalized = normalize(code);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        return invitationMapper.selectOne(new LambdaQueryWrapper<AuthInvitationEntity>()
            .eq(AuthInvitationEntity::getCode, normalized.toUpperCase(Locale.ROOT)));
    }

    private AuthInvitationEntity findReusableActivation(Long userId) {
        return invitationMapper.selectList(new LambdaQueryWrapper<AuthInvitationEntity>()
                .eq(AuthInvitationEntity::getKind, KIND_ACTIVATE)
                .eq(AuthInvitationEntity::getUserId, userId)
                .eq(AuthInvitationEntity::getStatus, ACTIVE))
            .stream()
            .filter(invitation -> checkUsable(invitation) == null)
            .findFirst()
            .orElse(null);
    }

    private void insertWithGeneratedCode(AuthInvitationEntity entity, String prefix) {
        // 唯一键 (code, deleted) 兜底,碰撞概率极低,重试三次足够
        for (int attempt = 0; attempt < 3; attempt++) {
            entity.setCode(prefix + randomCode(10));
            try {
                invitationMapper.insert(entity);
                return;
            } catch (DuplicateKeyException exception) {
                // 换码重试
            }
        }
        throw new BusinessException(ResultCode.INTERNAL_ERROR, "凭证码生成失败,请重试");
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private LocalDateTime expiresAt(Integer validDays) {
        return validDays == null ? null : LocalDateTime.now().plusDays(validDays);
    }

    private InvitationPrecheckView invalid(String message) {
        return new InvitationPrecheckView(false, null, null, null, null, null, message);
    }

    private InvitationView toView(AuthInvitationEntity entity) {
        return toViews(List.of(entity)).get(0);
    }

    /** 批量补齐机构名/角色名/用户名,避免分页列表逐行查询。 */
    private List<InvitationView> toViews(List<AuthInvitationEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Set<Long> institutionIds = entities.stream()
            .map(AuthInvitationEntity::getInstitutionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> institutionNames = institutionIds.isEmpty() ? Map.of()
            : institutionMapper.selectBatchIds(institutionIds).stream()
                .collect(Collectors.toMap(InstitutionEntity::getId, InstitutionEntity::getOrgName, (l, r) -> l));
        Set<String> roleCodes = entities.stream()
            .map(AuthInvitationEntity::getRoleCode).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> roleNames = roleCodes.isEmpty() ? Map.of()
            : roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>().in(RoleEntity::getRoleCode, roleCodes))
                .stream().collect(Collectors.toMap(RoleEntity::getRoleCode, RoleEntity::getRoleName, (l, r) -> l));
        Set<Long> userIds = entities.stream()
            .map(AuthInvitationEntity::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> usernames = userIds.isEmpty() ? Map.of()
            : userAccountMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserAccountEntity::getId, UserAccountEntity::getUsername, (l, r) -> l));

        return entities.stream().map(entity -> new InvitationView(
            entity.getId(),
            entity.getCode(),
            entity.getKind(),
            entity.getInstitutionId(),
            pick(institutionNames, entity.getInstitutionId()),
            entity.getUserId(),
            pick(usernames, entity.getUserId()),
            entity.getRoleCode(),
            pick(roleNames, entity.getRoleCode()),
            entity.getMaxUses(),
            entity.getUsedCount(),
            entity.getExpiresAt(),
            entity.getStatus(),
            entity.getRemark(),
            entity.getCreatedAt()
        )).toList();
    }

    /** INVITE 凭证没有 userId、ACTIVATE 凭证没有 roleCode,不可变 Map 对 null 键 get 会 NPE,统一走这里。 */
    private static <K, V> V pick(Map<K, V> map, K key) {
        return key == null ? null : map.get(key);
    }

    private void assertInstitutionExists(Long institutionId) {
        InstitutionEntity institution = institutionMapper.selectById(institutionId);
        if (institution == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "机构不存在：" + institutionId);
        }
    }

    private RoleEntity requireActiveRole(String roleCode) {
        RoleEntity role = findActiveRole(roleCode);
        if (role == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色不存在或已停用：" + roleCode);
        }
        return role;
    }

    private RoleEntity findActiveRole(String roleCode) {
        return roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
            .eq(RoleEntity::getRoleCode, normalize(roleCode))
            .eq(RoleEntity::getStatus, ACTIVE));
    }

    private void assertUniqueUsername(String username) {
        Long count = userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccountEntity>()
            .eq(UserAccountEntity::getUsername, username));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名已存在：" + username);
        }
    }

    private <T> void eqIfPresent(LambdaQueryWrapper<AuthInvitationEntity> wrapper,
                                 com.baomidou.mybatisplus.core.toolkit.support.SFunction<AuthInvitationEntity, T> column,
                                 String value) {
        String normalized = normalize(value);
        if (normalized != null && !normalized.isBlank()) {
            wrapper.eq(column, normalized);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
