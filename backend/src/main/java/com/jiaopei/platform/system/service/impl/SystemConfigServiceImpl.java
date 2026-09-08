package com.jiaopei.platform.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.common.api.PageResult;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.system.domain.entity.SystemConfigEntity;
import com.jiaopei.platform.system.dto.SystemConfigCreateRequest;
import com.jiaopei.platform.system.dto.SystemConfigPageQuery;
import com.jiaopei.platform.system.dto.SystemConfigUpdateRequest;
import com.jiaopei.platform.system.dto.SystemConfigView;
import com.jiaopei.platform.system.mapper.SystemConfigMapper;
import com.jiaopei.platform.system.service.SystemConfigService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;

    public SystemConfigServiceImpl(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    @Override
    public List<SystemConfigView> listConfigs() {
        return systemConfigMapper.selectList(null).stream()
            .map(this::toView)
            .toList();
    }

    @Override
    public PageResult<SystemConfigView> pageConfigs(SystemConfigPageQuery query) {
        LambdaQueryWrapper<SystemConfigEntity> wrapper = new LambdaQueryWrapper<>();
        String keyword = query.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(SystemConfigEntity::getConfigKey, trimmed)
                .or().like(SystemConfigEntity::getDisplayName, trimmed)
                .or().like(SystemConfigEntity::getDescription, trimmed));
        }
        String configGroup = query.getConfigGroup();
        if (configGroup != null && !configGroup.isBlank()) {
            wrapper.eq(SystemConfigEntity::getConfigGroup, configGroup.trim());
        }
        String status = query.getStatus();
        if (status != null && !status.isBlank()) {
            wrapper.eq(SystemConfigEntity::getStatus, status.trim());
        }
        wrapper.orderByAsc(SystemConfigEntity::getSortOrder)
            .orderByAsc(SystemConfigEntity::getConfigKey);

        Page<SystemConfigEntity> page = systemConfigMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords().stream().map(this::toView).toList());
    }

    @Override
    public SystemConfigView getConfig(Long id) {
        SystemConfigEntity entity = systemConfigMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "配置项不存在");
        }
        return toView(entity);
    }

    @Override
    @Cacheable(value = "systemConfig", key = "#key", unless = "#result == null")
    public String getConfigValue(String key, String defaultValue) {
        LambdaQueryWrapper<SystemConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfigEntity::getConfigKey, key)
               .eq(SystemConfigEntity::getStatus, "active");
        SystemConfigEntity entity = systemConfigMapper.selectOne(wrapper);
        return entity != null ? entity.getConfigValue() : defaultValue;
    }

    @Override
    public Boolean getBooleanConfig(String key, Boolean defaultValue) {
        String value = getConfigValue(key, null);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    @Override
    public Integer getIntConfig(String key, Integer defaultValue) {
        String value = getConfigValue(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemConfig", key = "#result.configKey()")
    public SystemConfigView createConfig(SystemConfigCreateRequest request) {
        LambdaQueryWrapper<SystemConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfigEntity::getConfigKey, request.configKey());
        if (systemConfigMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "配置键已存在");
        }

        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigKey(request.configKey());
        entity.setConfigValue(request.configValue());
        entity.setValueType(request.valueType());
        entity.setConfigGroup(request.configGroup());
        entity.setDisplayName(request.displayName());
        entity.setDescription(request.description());
        entity.setDefaultValue(request.defaultValue());
        entity.setIsSystem(request.isSystem());
        entity.setSortOrder(request.sortOrder());
        entity.setStatus(request.status());

        systemConfigMapper.insert(entity);
        return toView(entity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemConfig", allEntries = true)
    public SystemConfigView updateConfig(Long id, SystemConfigUpdateRequest request) {
        SystemConfigEntity entity = systemConfigMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "配置项不存在");
        }

        entity.setConfigValue(request.configValue());
        entity.setDisplayName(request.displayName());
        entity.setDescription(request.description());
        entity.setSortOrder(request.sortOrder());
        entity.setStatus(request.status());

        systemConfigMapper.updateById(entity);
        return toView(entity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemConfig", allEntries = true)
    public void deleteConfig(Long id) {
        SystemConfigEntity entity = systemConfigMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "配置项不存在");
        }
        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "系统内置配置不允许删除");
        }

        systemConfigMapper.deleteById(id);
    }

    private SystemConfigView toView(SystemConfigEntity entity) {
        return new SystemConfigView(
            entity.getId(),
            entity.getConfigKey(),
            entity.getConfigValue(),
            entity.getValueType(),
            entity.getConfigGroup(),
            entity.getDisplayName(),
            entity.getDescription(),
            entity.getDefaultValue(),
            entity.getIsSystem(),
            entity.getSortOrder(),
            entity.getStatus()
        );
    }
}
