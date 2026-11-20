package com.cornerstone.core.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.RoleDataScopeEntity;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.mapper.RoleDataScopeMapper;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.scope.RoleDataScopeDimension;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.RoleDataScopeUpdate;
import com.cornerstone.core.system.dto.RoleDataScopeView;
import com.cornerstone.core.system.service.RoleDataScopeService;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleDataScopeServiceImpl implements RoleDataScopeService {

    static final String SCOPE_ALL = "ALL";
    static final String SCOPE_PART = "PART";
    /** 该维度全部节点的哨兵值，与日历等业务表的 0 约定一致 */
    static final long ALL_VALUE = 0L;
    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    private final RoleDataScopeMapper roleDataScopeMapper;
    private final RoleMapper roleMapper;
    private final InstitutionMapper institutionMapper;
    /** 各业务模块注册的维度；模块未装配时列表为空，界面整块隐藏 */
    private final List<RoleDataScopeDimension> dimensions;

    public RoleDataScopeServiceImpl(RoleDataScopeMapper roleDataScopeMapper,
                                    RoleMapper roleMapper,
                                    InstitutionMapper institutionMapper,
                                    List<RoleDataScopeDimension> dimensions) {
        this.roleDataScopeMapper = roleDataScopeMapper;
        this.roleMapper = roleMapper;
        this.institutionMapper = institutionMapper;
        this.dimensions = dimensions;
    }

    @Override
    public List<RoleDataScopeView> list(Long roleId, Long institutionId) {
        requireRole(roleId);
        Long resolvedInstitutionId = resolveInstitution(institutionId);
        List<RoleDataScopeView> result = new ArrayList<>();
        for (RoleDataScopeDimension dimension : dimensions) {
            List<Long> values = rows(roleId, resolvedInstitutionId, dimension.dimensionKey()).stream()
                .map(RoleDataScopeEntity::getScopeValue)
                .toList();
            boolean configured = !values.isEmpty();
            boolean all = !configured || values.contains(ALL_VALUE);
            result.add(new RoleDataScopeView(
                dimension.dimensionKey(),
                dimension.dimensionLabel(),
                dimension.allOptionLabel(),
                dimension.partOptionLabel(),
                all ? SCOPE_ALL : SCOPE_PART,
                configured,
                all ? List.of() : values,
                dimension.options(resolvedInstitutionId)
            ));
        }
        return result;
    }

    @Override
    @Transactional
    public void replace(Long roleId, Long institutionId, List<RoleDataScopeUpdate> updates) {
        if (updates == null) {
            return;
        }
        requireRole(roleId);
        Long resolvedInstitutionId = resolveInstitution(institutionId);
        Map<String, RoleDataScopeDimension> dimensionByKey = dimensions.stream()
            .collect(Collectors.toMap(RoleDataScopeDimension::dimensionKey, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));
        Set<String> seen = new LinkedHashSet<>();
        for (RoleDataScopeUpdate update : updates) {
            if (!seen.add(update.dimensionKey())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "数据范围维度重复：" + update.dimensionKey());
            }
            RoleDataScopeDimension dimension = dimensionByKey.get(update.dimensionKey());
            if (dimension == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "未知的数据范围维度：" + update.dimensionKey());
            }
            List<Long> values = normalizeValues(update.values());
            if (SCOPE_ALL.equals(update.scopeMode())) {
                if (!values.isEmpty()) {
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                        "选择「全部」时不能再指定节点");
                }
                replaceRows(roleId, resolvedInstitutionId, dimension.dimensionKey(), List.of(ALL_VALUE));
            } else if (SCOPE_PART.equals(update.scopeMode())) {
                if (values.isEmpty()) {
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                        "指定范围至少要选择一个节点");
                }
                if (values.contains(ALL_VALUE)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                        "指定范围不能包含「全部」节点");
                }
                dimension.validate(resolvedInstitutionId, values);
                replaceRows(roleId, resolvedInstitutionId, dimension.dimensionKey(), values);
            } else {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "数据范围模式只能是 ALL 或 PART");
            }
        }
    }

    private void replaceRows(Long roleId, Long institutionId, String scopeType, List<Long> values) {
        roleDataScopeMapper.physicalDelete(roleId, institutionId, scopeType);
        for (Long value : values) {
            RoleDataScopeEntity entity = new RoleDataScopeEntity();
            entity.setRoleId(roleId);
            entity.setInstitutionId(institutionId);
            entity.setScopeType(scopeType);
            entity.setScopeValue(value);
            entity.setDeleted(Boolean.FALSE);
            roleDataScopeMapper.insert(entity);
        }
    }

    private List<RoleDataScopeEntity> rows(Long roleId, Long institutionId, String scopeType) {
        return roleDataScopeMapper.selectList(new LambdaQueryWrapper<RoleDataScopeEntity>()
            .eq(RoleDataScopeEntity::getRoleId, roleId)
            .eq(RoleDataScopeEntity::getInstitutionId, institutionId)
            .eq(RoleDataScopeEntity::getScopeType, scopeType));
    }

    private List<Long> normalizeValues(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    /**
     * 机构口径：普通用户只能操作自己所属机构；平台管理员可以为任意已存在机构配置。
     * 范围按「角色 + 机构」存储，避免全局角色在 A 机构配置后锁死 B 机构持有人。
     */
    private Long resolveInstitution(Long requestedInstitutionId) {
        AuthenticatedUser user = AuthContext.requireUser();
        Long current = user.orgId();
        if (requestedInstitutionId == null) {
            if (current == null) {
                throw new BusinessException(ResultCode.FORBIDDEN,
                    "当前登录账号未归属任何机构，无法配置数据范围");
            }
            return current;
        }
        if (Objects.equals(requestedInstitutionId, current)) {
            return current;
        }
        if (user.roles() != null && user.roles().contains(PLATFORM_ADMIN_ROLE)) {
            InstitutionEntity institution = institutionMapper.selectById(requestedInstitutionId);
            if (institution == null) {
                throw new BusinessException(ResultCode.NOT_FOUND,
                    "机构不存在：" + requestedInstitutionId);
            }
            return requestedInstitutionId;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权配置其他机构的数据范围");
    }

    private RoleEntity requireRole(Long roleId) {
        RoleEntity role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在：" + roleId);
        }
        return role;
    }
}
