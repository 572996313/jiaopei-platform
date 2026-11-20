package com.cornerstone.module.organization.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.scope.RoleDataScopeDimension;
import com.cornerstone.core.auth.scope.RoleDataScopeOption;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 校区数据范围维度的选项与校验。core 只认 SPI，不感知 jp_campus。
 */
@Component("campusRoleDataScopeDimension")
public class CampusRoleDataScopeDimension implements RoleDataScopeDimension {

    private static final String CLOSED = "closed";

    private final CampusMapper campusMapper;

    public CampusRoleDataScopeDimension(CampusMapper campusMapper) {
        this.campusMapper = campusMapper;
    }

    @Override
    public String dimensionKey() {
        return CampusScopeGuard.DIMENSION_KEY;
    }

    @Override
    public String dimensionLabel() {
        return "可访问校区";
    }

    @Override
    public String allOptionLabel() {
        return "全部校区";
    }

    @Override
    public String partOptionLabel() {
        return "指定校区";
    }

    @Override
    public List<RoleDataScopeOption> options(Long institutionId) {
        if (institutionId == null) {
            return List.of();
        }
        return campusMapper.selectList(new LambdaQueryWrapper<CampusEntity>()
                .eq(CampusEntity::getInstitutionId, institutionId)
                .orderByAsc(CampusEntity::getSortNo)
                .orderByAsc(CampusEntity::getCampusCode))
            .stream()
            .map(campus -> new RoleDataScopeOption(
                campus.getId(),
                campus.getCampusCode(),
                campus.getCampusName(),
                campus.getStatus(),
                // 已关闭校区仍返回，便于从已授权列表里移除；前端把它置灰
                campus.getStatus() == null || !CLOSED.equalsIgnoreCase(campus.getStatus())))
            .toList();
    }

    @Override
    public void validate(Long institutionId, Collection<Long> values) {
        List<Long> distinct = values == null ? List.of()
            : values.stream().filter(Objects::nonNull).distinct().toList();
        if (institutionId == null || distinct.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "指定校区不能为空");
        }
        Long owned = campusMapper.selectCount(new LambdaQueryWrapper<CampusEntity>()
            .eq(CampusEntity::getInstitutionId, institutionId)
            .in(CampusEntity::getId, distinct));
        if (owned == null || owned != distinct.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "所选校区不属于当前机构或已删除");
        }
    }
}
