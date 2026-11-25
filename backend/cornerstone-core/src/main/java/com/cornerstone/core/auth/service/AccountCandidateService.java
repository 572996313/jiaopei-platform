package com.cornerstone.core.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
import com.cornerstone.core.auth.dto.AccountOptionView;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.security.UserAccessTokenService;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 第三方登录的候选账号展示：只下发显示名、端与机构名，
 * 账号键是本次票据内的序号，前端拿不到也传不进真实 userId。
 */
@Component
public class AccountCandidateService {

    private static final String ACTIVE = "active";

    private final UserAccountMapper userAccountMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final RoleMapper roleMapper;
    private final InstitutionMapper institutionMapper;
    private final UserAccessTokenService userAccessTokenService;

    public AccountCandidateService(UserAccountMapper userAccountMapper,
                                   UserRoleRelMapper userRoleRelMapper,
                                   RoleMapper roleMapper,
                                   InstitutionMapper institutionMapper,
                                   UserAccessTokenService userAccessTokenService) {
        this.userAccountMapper = userAccountMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.roleMapper = roleMapper;
        this.institutionMapper = institutionMapper;
        this.userAccessTokenService = userAccessTokenService;
    }

    /** 过滤出启用中的账号，保持传入顺序（票据里的序号与候选列表一一对应） */
    public List<Long> activeUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(userIds));
        Map<Long, UserAccountEntity> users = loadUsers(distinct);
        return distinct.stream()
            .filter(id -> users.containsKey(id) && ACTIVE.equalsIgnoreCase(users.get(id).getStatus()))
            .toList();
    }

    public List<AccountOptionView> build(List<Long> userIds) {
        Map<Long, UserAccountEntity> users = loadUsers(userIds);
        Map<Long, Set<String>> roleCodes = loadRoleCodes(userIds);
        Map<Long, String> institutionNames = loadInstitutionNames(users.values());

        List<AccountOptionView> options = new ArrayList<>();
        int index = 0;
        for (Long userId : new ArrayList<>(new LinkedHashSet<>(userIds))) {
            UserAccountEntity user = users.get(userId);
            if (user == null || !ACTIVE.equalsIgnoreCase(user.getStatus())) {
                continue;
            }
            String portal = userAccessTokenService.resolvePortal(
                user.getRegisterSource(), roleCodes.getOrDefault(userId, Set.of()));
            String displayName = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
            options.add(new AccountOptionView(
                String.valueOf(index++),
                displayName,
                portal,
                institutionNames.getOrDefault(user.getInstitutionId(), "")));
        }
        return options;
    }

    /** 管理后台端才允许从网页端登录：家长/学生的页面只在小程序里 */
    public void assertWebPortal(Long userId) {
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在");
        }
        Set<String> roleCodes = loadRoleCodes(List.of(userId)).getOrDefault(userId, Set.of());
        String portal = userAccessTokenService.resolvePortal(user.getRegisterSource(), roleCodes);
        if (!PortalResolver.ADMIN.equals(portal)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该账号请使用微信小程序登录");
        }
    }

    private Map<Long, UserAccountEntity> loadUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserAccountEntity> users = new HashMap<>();
        for (UserAccountEntity user : userAccountMapper.selectBatchIds(userIds)) {
            users.put(user.getId(), user);
        }
        return users;
    }

    private Map<Long, Set<String>> loadRoleCodes(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserRoleRelEntity> relations = userRoleRelMapper.selectList(
            new LambdaQueryWrapper<UserRoleRelEntity>().in(UserRoleRelEntity::getUserId, userIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = relations.stream().map(UserRoleRelEntity::getRoleId).distinct().toList();
        Map<Long, RoleEntity> roles = new HashMap<>();
        for (RoleEntity role : roleMapper.selectBatchIds(roleIds)) {
            if (ACTIVE.equalsIgnoreCase(role.getStatus())) {
                roles.put(role.getId(), role);
            }
        }
        Map<Long, Set<String>> result = new HashMap<>();
        for (UserRoleRelEntity relation : relations) {
            RoleEntity role = roles.get(relation.getRoleId());
            if (role != null) {
                result.computeIfAbsent(relation.getUserId(), key -> new LinkedHashSet<>())
                    .add(role.getRoleCode());
            }
        }
        return result;
    }

    private Map<Long, String> loadInstitutionNames(Iterable<UserAccountEntity> users) {
        Set<Long> institutionIds = new LinkedHashSet<>();
        for (UserAccountEntity user : users) {
            if (user.getInstitutionId() != null) {
                institutionIds.add(user.getInstitutionId());
            }
        }
        if (institutionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (InstitutionEntity institution : institutionMapper.selectBatchIds(institutionIds)) {
            String name = institution.getOrgName() == null || institution.getOrgName().isBlank()
                ? institution.getShortName() : institution.getOrgName();
            names.put(institution.getId(), name == null ? "" : name);
        }
        return names;
    }
}
