package com.cornerstone.core.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.dto.RoleCreateRequest;
import com.cornerstone.core.auth.dto.RolePageQuery;
import com.cornerstone.core.auth.dto.RoleUpdateRequest;
import com.cornerstone.core.auth.dto.RoleView;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.service.SystemRoleService;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.util.Arrays;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemRoleServiceImpl implements SystemRoleService {

    private static final String ACTIVE = "active";

    private final RoleMapper roleMapper;

    public SystemRoleServiceImpl(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public List<RoleView> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .orderByAsc(RoleEntity::getRoleCode))
            .stream()
            .map(this::toView)
            .toList();
    }

    @Override
    public PageResult<RoleView> pageRoles(RolePageQuery query) {
        LambdaQueryWrapper<RoleEntity> wrapper = new LambdaQueryWrapper<>();
        String keyword = normalize(query.getKeyword());
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(RoleEntity::getRoleCode, keyword)
                .or().like(RoleEntity::getRoleName, keyword));
        }
        String status = normalize(query.getStatus());
        if (status != null && !status.isBlank()) {
            wrapper.eq(RoleEntity::getStatus, status);
        }
        wrapper.orderByAsc(RoleEntity::getRoleCode);

        Page<RoleEntity> page = roleMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords().stream().map(this::toView).toList());
    }

    @Override
    @Transactional
    public RoleView createRole(RoleCreateRequest request) {
        assertUniqueRoleCode(normalize(request.roleCode()));
        RoleEntity entity = new RoleEntity();
        entity.setRoleCode(normalize(request.roleCode()));
        entity.setRoleName(normalize(request.roleName()));
        entity.setPermissionCodes(joinPermissionCodes(request.permissionCodes()));
        entity.setStatus(defaultStatus(request.status()));
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            roleMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色编码已存在：" + entity.getRoleCode());
        }
        return toView(entity);
    }

    @Override
    @Transactional
    public RoleView updateRole(Long id, RoleUpdateRequest request) {
        RoleEntity entity = requireRole(id);
        entity.setRoleName(normalize(request.roleName()));
        entity.setPermissionCodes(joinPermissionCodes(request.permissionCodes()));
        entity.setStatus(normalize(request.status()));
        entity.setRemark(request.remark());
        roleMapper.updateById(entity);
        return toView(entity);
    }

    private RoleEntity requireRole(Long id) {
        RoleEntity entity = roleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在：" + id);
        }
        return entity;
    }

    private void assertUniqueRoleCode(String roleCode) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<RoleEntity>()
            .eq(RoleEntity::getRoleCode, roleCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色编码已存在：" + roleCode);
        }
    }

    private RoleView toView(RoleEntity entity) {
        return new RoleView(
            entity.getId(),
            entity.getRoleCode(),
            entity.getRoleName(),
            splitPermissionCodes(entity.getPermissionCodes()),
            entity.getStatus(),
            entity.getRemark()
        );
    }

    private String joinPermissionCodes(List<String> permissionCodes) {
        return permissionCodes == null ? "" : permissionCodes.stream()
            .map(this::normalize)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .reduce((left, right) -> left + "," + right)
            .orElse("");
    }

    private List<String> splitPermissionCodes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    private String defaultStatus(String status) {
        String normalized = normalize(status);
        return normalized == null || normalized.isBlank() ? ACTIVE : normalized;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
