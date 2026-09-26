package com.cornerstone.core.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.domain.entity.DataSourceEntity;
import com.cornerstone.core.system.dto.DataSourceCreateRequest;
import com.cornerstone.core.system.dto.DataSourceDefinitionView;
import com.cornerstone.core.system.dto.DataSourceUpdateRequest;
import com.cornerstone.core.system.dto.DataSourceView;
import com.cornerstone.core.system.mapper.DataSourceMapper;
import com.cornerstone.core.system.service.DataSourceService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
public class DataSourceServiceImpl implements DataSourceService {

    private static final String ACTIVE = "active";
    private static final String TYPE_API = "api";
    private static final String TYPE_DICT = "dict";
    private static final String DEFAULT_VALUE_KEY = "id";

    private static final Set<String> SOURCE_TYPES = Set.of(TYPE_API, TYPE_DICT);
    private static final Set<String> METHODS = Set.of("GET", "POST");

    private final DataSourceMapper dataSourceMapper;
    private final ObjectMapper objectMapper;

    public DataSourceServiceImpl(DataSourceMapper dataSourceMapper, ObjectMapper objectMapper) {
        this.dataSourceMapper = dataSourceMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DataSourceView> listDataSources() {
        return dataSourceMapper.selectList(new LambdaQueryWrapper<DataSourceEntity>()
                .orderByAsc(DataSourceEntity::getSourceCode))
            .stream()
            .map(this::toView)
            .toList();
    }

    @Override
    public Map<String, DataSourceDefinitionView> getDefinitions(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        List<String> distinct = codes.stream().filter(StringUtils::hasText).distinct().toList();
        Map<String, DataSourceDefinitionView> result = new LinkedHashMap<>();
        for (String code : distinct) {
            DataSourceDefinitionView definition = getDefinition(code);
            if (definition != null) {
                result.put(code, definition);
            }
        }
        return result;
    }

    @Override
    @Cacheable(value = "dataSource", key = "#code", unless = "#result == null")
    public DataSourceDefinitionView getDefinition(String code) {
        DataSourceEntity entity = dataSourceMapper.selectOne(new LambdaQueryWrapper<DataSourceEntity>()
            .eq(DataSourceEntity::getSourceCode, code)
            .eq(DataSourceEntity::getStatus, ACTIVE));
        return entity == null ? null : toDefinition(entity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "dataSource", allEntries = true)
    public DataSourceView createDataSource(DataSourceCreateRequest request) {
        validateSource(request.sourceType(), request.endpoint(), request.dictType());
        validateParamDefs(request.paramDefs());
        assertUniqueSourceCode(request.sourceCode());

        DataSourceEntity entity = new DataSourceEntity();
        entity.setSourceCode(request.sourceCode());
        entity.setSourceName(request.sourceName());
        entity.setEndpoint(request.endpoint());
        entity.setMethod(defaultMethod(request.method()));
        entity.setValueKey(request.valueKey());
        entity.setLabelKey(request.labelKey());
        entity.setParamDefs(request.paramDefs());
        entity.setSourceType(request.sourceType());
        entity.setDictType(request.dictType());
        entity.setDescription(request.description());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
        try {
            dataSourceMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "数据源编码已存在:" + request.sourceCode());
        }
        return toView(entity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "dataSource", allEntries = true)
    public DataSourceView updateDataSource(Long id, DataSourceUpdateRequest request) {
        DataSourceEntity entity = requireDataSource(id);
        validateSource(request.sourceType(), request.endpoint(), request.dictType());
        validateParamDefs(request.paramDefs());

        entity.setSourceName(request.sourceName());
        entity.setEndpoint(request.endpoint());
        entity.setMethod(defaultMethod(request.method()));
        entity.setValueKey(request.valueKey());
        entity.setLabelKey(request.labelKey());
        entity.setParamDefs(request.paramDefs());
        entity.setSourceType(request.sourceType());
        entity.setDictType(request.dictType());
        entity.setDescription(request.description());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
        dataSourceMapper.updateById(entity);
        return toView(entity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "dataSource", allEntries = true)
    public void deleteDataSource(Long id) {
        DataSourceEntity entity = requireDataSource(id);
        // 种子配置(id 1-6)允许编辑但不允许删除,避免误删导致下拉选项失效
        if (entity.getId() != null && entity.getId() <= 6) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "内置数据源不允许删除,可改为停用");
        }
        dataSourceMapper.physicalDeleteById(id);
    }

    private DataSourceEntity requireDataSource(Long id) {
        DataSourceEntity entity = dataSourceMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据源不存在:" + id);
        }
        return entity;
    }

    private void assertUniqueSourceCode(String sourceCode) {
        Long count = dataSourceMapper.selectCount(new LambdaQueryWrapper<DataSourceEntity>()
            .eq(DataSourceEntity::getSourceCode, sourceCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "数据源编码已存在:" + sourceCode);
        }
    }

    /**
     * 校验数据源类型与接口字段的搭配关系。api 类型必须提供 endpoint;
     * dict 类型必须提供 dictType,后端不校验接口路径(前端直接读字典)。
     */
    private void validateSource(String sourceType, String endpoint, String dictType) {
        if (sourceType == null || !SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "数据源类型不支持:" + sourceType + ",可选值 " + SOURCE_TYPES);
        }
        if (TYPE_API.equals(sourceType)) {
            if (!StringUtils.hasText(endpoint) || !endpoint.startsWith("/")) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "api 类型数据源必须提供以 / 开头的接口路径");
            }
        } else if (!StringUtils.hasText(dictType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "dict 类型数据源必须提供字典类型编码 dictType");
        }
    }

    /**
     * 校验请求参数定义 param_defs:整体为 JSON 数组,每项是含 name 的对象。
     * 为空(null/空白)表示无参数,不校验。
     */
    private void validateParamDefs(String paramDefs) {
        if (paramDefs == null || paramDefs.isBlank()) {
            return;
        }
        try {
            List<Map<String, Object>> params = objectMapper.readValue(paramDefs, new TypeReference<>() {
            });
            for (int i = 0; i < params.size(); i++) {
                Map<String, Object> param = params.get(i);
                String name = param.get("name") instanceof String text ? text : null;
                if (name == null || name.isBlank()) {
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                        "请求参数定义 paramDefs[" + i + "] 缺少必填字段 name");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "请求参数定义 paramDefs 不是合法的 JSON 数组:" + exception.getOriginalMessage());
        }
    }

    private String defaultMethod(String method) {
        if (method == null || method.isBlank()) {
            return "GET";
        }
        String upper = method.trim().toUpperCase();
        if (!METHODS.contains(upper)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求方法不支持:" + method + ",可选值 " + METHODS);
        }
        return upper;
    }

    private DataSourceDefinitionView toDefinition(DataSourceEntity entity) {
        return new DataSourceDefinitionView(
            entity.getSourceCode(),
            entity.getSourceName(),
            entity.getEndpoint(),
            entity.getMethod(),
            defaultKey(entity.getValueKey(), DEFAULT_VALUE_KEY),
            entity.getLabelKey(),
            entity.getParamDefs(),
            entity.getSourceType(),
            entity.getDictType()
        );
    }

    private String defaultKey(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private DataSourceView toView(DataSourceEntity entity) {
        return new DataSourceView(
            entity.getId(),
            entity.getSourceCode(),
            entity.getSourceName(),
            entity.getEndpoint(),
            entity.getMethod(),
            entity.getValueKey(),
            entity.getLabelKey(),
            entity.getParamDefs(),
            entity.getSourceType(),
            entity.getDictType(),
            entity.getDescription(),
            entity.getStatus(),
            entity.getRemark()
        );
    }
}