package com.jiaopei.platform.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.system.domain.entity.ListConfigEntity;
import com.jiaopei.platform.system.dto.ListConfigCreateRequest;
import com.jiaopei.platform.system.dto.ListConfigUpdateRequest;
import com.jiaopei.platform.system.dto.ListConfigView;
import com.jiaopei.platform.system.mapper.ListConfigMapper;
import com.jiaopei.platform.system.service.ListConfigService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ListConfigServiceImpl implements ListConfigService {

    private static final String ACTIVE = "active";

    /** 列渲染类型:文本(默认)/日期时间/字典/布尔/标签数组/静态值映射 */
    private static final Set<String> COLUMN_TYPES = Set.of("text", "datetime", "dict", "boolean", "tags", "map");

    /** 查询控件类型:输入框(默认)/下拉/字典下拉/日期 */
    private static final Set<String> QUERY_FIELD_TYPES = Set.of("input", "select", "dict-select", "date");

    private final ListConfigMapper listConfigMapper;
    private final ObjectMapper objectMapper;

    public ListConfigServiceImpl(ListConfigMapper listConfigMapper, ObjectMapper objectMapper) {
        this.listConfigMapper = listConfigMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ListConfigView> listConfigs() {
        return listConfigMapper.selectList(new LambdaQueryWrapper<ListConfigEntity>()
                .orderByAsc(ListConfigEntity::getResourceCode))
            .stream()
            .map(this::toView)
            .toList();
    }

    @Override
    @Cacheable(value = "listConfig", key = "#resourceCode", unless = "#result == null")
    public ListConfigView getByResourceCode(String resourceCode) {
        ListConfigEntity entity = listConfigMapper.selectOne(new LambdaQueryWrapper<ListConfigEntity>()
            .eq(ListConfigEntity::getResourceCode, resourceCode)
            .eq(ListConfigEntity::getStatus, ACTIVE));
        return entity == null ? null : toView(entity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "listConfig", allEntries = true)
    public ListConfigView createConfig(ListConfigCreateRequest request) {
        validateEndpoint(request.endpoint());
        validatePayload(request.columnsJson(), request.queryJson());
        assertUniqueResourceCode(request.resourceCode());

        ListConfigEntity entity = new ListConfigEntity();
        entity.setResourceCode(request.resourceCode());
        entity.setResourceName(request.resourceName());
        entity.setEndpoint(request.endpoint());
        entity.setPageSize(request.pageSize());
        entity.setColumnsJson(request.columnsJson());
        entity.setQueryJson(request.queryJson());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
        try {
            listConfigMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "列表配置编码已存在：" + request.resourceCode());
        }
        return toView(entity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "listConfig", allEntries = true)
    public ListConfigView updateConfig(Long id, ListConfigUpdateRequest request) {
        ListConfigEntity entity = requireConfig(id);
        validateEndpoint(request.endpoint());
        validatePayload(request.columnsJson(), request.queryJson());

        entity.setResourceName(request.resourceName());
        entity.setEndpoint(request.endpoint());
        entity.setPageSize(request.pageSize());
        entity.setColumnsJson(request.columnsJson());
        entity.setQueryJson(request.queryJson());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
        listConfigMapper.updateById(entity);
        return toView(entity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "listConfig", allEntries = true)
    public void deleteConfig(Long id) {
        ListConfigEntity entity = requireConfig(id);
        // 种子配置(id 1-3)允许编辑但不允许删除,避免误删导致系统设置三个列表页不可用
        if (entity.getId() != null && entity.getId() <= 3) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "内置列表配置不允许删除,可改为停用");
        }
        listConfigMapper.deleteById(entity);
    }

    private ListConfigEntity requireConfig(Long id) {
        ListConfigEntity entity = listConfigMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "列表配置不存在：" + id);
        }
        return entity;
    }

    private void assertUniqueResourceCode(String resourceCode) {
        Long count = listConfigMapper.selectCount(new LambdaQueryWrapper<ListConfigEntity>()
            .eq(ListConfigEntity::getResourceCode, resourceCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "列表配置编码已存在：" + resourceCode);
        }
    }

    private void validateEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint) || !endpoint.startsWith("/")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接口路径必须以 / 开头,如 /system/users/page");
        }
    }

    /**
     * 校验列配置与查询条件配置的 JSON 结构:必填字段、渲染类型枚举、
     * 字典列必须带 dictType、映射列必须带 valueMap。非法输入直接拒绝,
     * 避免脏配置导致前端列表页崩溃。
     */
    private void validatePayload(String columnsJson, String queryJson) {
        List<Map<String, Object>> columns = parseArray(columnsJson, "列配置 columnsJson");
        if (columns.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "列配置 columnsJson 不能为空数组");
        }
        for (int i = 0; i < columns.size(); i++) {
            validateColumn(columns.get(i), "列配置[" + i + "]");
        }

        List<Map<String, Object>> queryFields = parseArray(queryJson, "查询条件配置 queryJson");
        for (int i = 0; i < queryFields.size(); i++) {
            validateQueryField(queryFields.get(i), "查询条件配置[" + i + "]");
        }
    }

    private void validateColumn(Map<String, Object> column, String where) {
        requireText(column, "prop", where);
        requireText(column, "label", where);
        String type = optionalText(column, "type");
        if (type != null && !COLUMN_TYPES.contains(type)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                where + " 的 type 不支持：" + type + ",可选值 " + COLUMN_TYPES);
        }
        if ("dict".equals(type)) {
            requireText(column, "dictType", where);
        }
        if ("map".equals(type)) {
            Object valueMap = column.get("valueMap");
            if (!(valueMap instanceof Map<?, ?> map) || map.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, where + " 的 type=map 时必须提供非空的 valueMap 对象");
            }
        }
        String renderer = optionalText(column, "renderer");
        if (renderer != null && renderer.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, where + " 的 renderer 不能为空字符串");
        }
    }

    private void validateQueryField(Map<String, Object> field, String where) {
        requireText(field, "field", where);
        requireText(field, "label", where);
        String type = optionalText(field, "type");
        if (type != null && !QUERY_FIELD_TYPES.contains(type)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                where + " 的 type 不支持：" + type + ",可选值 " + QUERY_FIELD_TYPES);
        }
        if ("dict-select".equals(type)) {
            requireText(field, "dictType", where);
        }
    }

    private List<Map<String, Object>> parseArray(String json, String name) {
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (list == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, name + "必须是 JSON 数组");
            }
            return list;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, name + " 不是合法的 JSON：" + exception.getOriginalMessage());
        }
    }

    private void requireText(Map<String, Object> source, String key, String where) {
        Object value = source.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, where + " 缺少必填字段 " + key);
        }
    }

    private String optionalText(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof String text ? text : null;
    }

    private ListConfigView toView(ListConfigEntity entity) {
        return new ListConfigView(
            entity.getId(),
            entity.getResourceCode(),
            entity.getResourceName(),
            entity.getEndpoint(),
            entity.getPageSize(),
            entity.getColumnsJson(),
            entity.getQueryJson(),
            entity.getStatus(),
            entity.getRemark()
        );
    }
}
