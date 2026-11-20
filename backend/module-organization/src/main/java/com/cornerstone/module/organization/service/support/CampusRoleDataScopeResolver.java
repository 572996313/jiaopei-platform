package com.cornerstone.module.organization.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.domain.RoleDataScopeEntity;
import com.cornerstone.core.auth.mapper.RoleDataScopeMapper;
import com.cornerstone.core.auth.scope.ResolvedDataScope;
import com.cornerstone.core.auth.scope.RoleDataScopeResolver;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 校区维度的登录快照解析。
 *
 * <p>并集口径逐角色判断：只要有一个角色没有配置或显式授了「全部」(0)，整体就不限制；
 * 否则把各角色的校区白名单并集返回。空角色列表视为不限制（这类用户本身也没有权限）。</p>
 */
@Component("campusRoleDataScopeResolver")
public class CampusRoleDataScopeResolver implements RoleDataScopeResolver {

    private final RoleDataScopeMapper roleDataScopeMapper;

    public CampusRoleDataScopeResolver(RoleDataScopeMapper roleDataScopeMapper) {
        this.roleDataScopeMapper = roleDataScopeMapper;
    }

    @Override
    public String dimensionKey() {
        return CampusScopeGuard.DIMENSION_KEY;
    }

    @Override
    public ResolvedDataScope resolve(Long institutionId, Collection<Long> roleIds) {
        if (institutionId == null || roleIds == null || roleIds.isEmpty()) {
            return null;
        }
        List<Long> distinctRoleIds = roleIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinctRoleIds.isEmpty()) {
            return null;
        }
        List<RoleDataScopeEntity> rows = roleDataScopeMapper.selectList(
            new LambdaQueryWrapper<RoleDataScopeEntity>()
                .eq(RoleDataScopeEntity::getInstitutionId, institutionId)
                .eq(RoleDataScopeEntity::getScopeType, dimensionKey())
                .in(RoleDataScopeEntity::getRoleId, distinctRoleIds));

        Map<Long, Set<Long>> valuesByRole = new LinkedHashMap<>();
        for (RoleDataScopeEntity row : rows) {
            valuesByRole.computeIfAbsent(row.getRoleId(), key -> new LinkedHashSet<>())
                .add(row.getScopeValue());
        }

        Set<Long> union = new LinkedHashSet<>();
        for (Long roleId : distinctRoleIds) {
            Set<Long> values = valuesByRole.get(roleId);
            // 未配置 = 不限制；显式 0 = 该维度全部；两者都让整体放开
            if (values == null || values.isEmpty() || values.contains(0L)) {
                return null;
            }
            union.addAll(values);
        }
        return new ResolvedDataScope(dimensionKey(), true, Set.copyOf(union));
    }
}
