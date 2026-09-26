package com.cornerstone.core.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.domain.entity.ListConfigEntity;
import com.cornerstone.core.system.dto.ListConfigCreateRequest;
import com.cornerstone.core.system.dto.ListConfigUpdateRequest;
import com.cornerstone.core.system.dto.ListConfigView;
import com.cornerstone.core.system.mapper.ListConfigMapper;
import com.cornerstone.core.system.service.ListConfigService;
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

    /** 列固定方向:宽表横向滚动时钉住首/尾列,缺省不固定 */
    private static final Set<String> COLUMN_FIXED_DIRECTIONS = Set.of("left", "right");

    /** 数据单元格对齐(表头不受影响,前端全站固定居中),缺省 left */
    private static final Set<String> COLUMN_ALIGNMENTS = Set.of("left", "center", "right");

    /** 查询控件类型:输入框(默认)/下拉/字典下拉/日期 */
    private static final Set<String> QUERY_FIELD_TYPES = Set.of("input", "select", "dict-select", "date");

    /** 按钮位置:表格上方工具栏 / 行内操作列 */
    private static final Set<String> ACTION_POSITIONS = Set.of("toolbar", "row");

    /** 按钮样式,与 Element Plus button type 对齐 */
    private static final Set<String> ACTION_BUTTON_TYPES = Set.of("primary", "default", "text", "danger");

    /** 表单控件类型(与前端 ConfigurableForm 控件枚举对齐) */
    private static final Set<String> FORM_FIELD_TYPES = Set.of(
        "input", "password", "textarea", "number", "select", "dict-select",
        "switch", "checkbox-group", "radio", "permission-groups");

    /** 表单显示/禁用模式取值 */
    private static final Set<String> FORM_MODES = Set.of("create", "edit");

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
        validateActions(request.actionsJson());
        validateForm(request.formJson());
        validatePageShell(request.pageJson());
        validateDialogShell(request.dialogJson());
        assertUniqueResourceCode(request.resourceCode());

        ListConfigEntity entity = new ListConfigEntity();
        entity.setResourceCode(request.resourceCode());
        entity.setResourceName(request.resourceName());
        entity.setEndpoint(request.endpoint());
        entity.setPageSize(request.pageSize());
        entity.setColumnsJson(request.columnsJson());
        entity.setQueryJson(request.queryJson());
        entity.setActionsJson(request.actionsJson());
        entity.setFormJson(request.formJson());
        entity.setPageJson(request.pageJson());
        entity.setDialogJson(request.dialogJson());
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
        validateActions(request.actionsJson());
        validateForm(request.formJson());
        validatePageShell(request.pageJson());
        validateDialogShell(request.dialogJson());

        entity.setResourceName(request.resourceName());
        entity.setEndpoint(request.endpoint());
        entity.setPageSize(request.pageSize());
        entity.setColumnsJson(request.columnsJson());
        entity.setQueryJson(request.queryJson());
        entity.setActionsJson(request.actionsJson());
        entity.setFormJson(request.formJson());
        entity.setPageJson(request.pageJson());
        entity.setDialogJson(request.dialogJson());
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
        listConfigMapper.physicalDeleteById(entity.getId());
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
        String fixed = optionalText(column, "fixed");
        if (fixed != null && !COLUMN_FIXED_DIRECTIONS.contains(fixed)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                where + " 的 fixed 不支持：" + fixed + ",可选值 " + COLUMN_FIXED_DIRECTIONS + " 或不填");
        }
        String align = optionalText(column, "align");
        if (align != null && !COLUMN_ALIGNMENTS.contains(align)) {
            // 可选值按固定顺序写出来:Set.of 的迭代顺序每次 JVM 启动都不同,拼进报错会让操作员以为是脏数据
            throw new BusinessException(ResultCode.BAD_REQUEST,
                where + " 的 align 不支持：" + align + ",可选值 left、center、right 或不填");
        }
        Object sortable = column.get("sortable");
        if (sortable != null && !(sortable instanceof Boolean)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, where + " 的 sortable 只能是 true 或 false");
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

    /**
     * 校验按钮配置 JSON 结构:action/label/position 必填、position 与 buttonType 取值合法。
     * actionsJson 为空(null/空白)表示未配置按钮,前端回退插槽模式,不校验。
     */
    private void validateActions(String actionsJson) {
        if (actionsJson == null || actionsJson.isBlank()) {
            return;
        }
        List<Map<String, Object>> actions = parseArray(actionsJson, "按钮配置 actionsJson");
        for (int i = 0; i < actions.size(); i++) {
            Map<String, Object> action = actions.get(i);
            String where = "按钮配置[" + i + "]";
            requireText(action, "action", where);
            requireText(action, "label", where);
            String position = optionalText(action, "position");
            if (position == null || !ACTION_POSITIONS.contains(position)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    where + " 缺少必填字段 position,可选值 " + ACTION_POSITIONS);
            }
            String buttonType = optionalText(action, "buttonType");
            if (buttonType != null && !ACTION_BUTTON_TYPES.contains(buttonType)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    where + " 的 buttonType 不支持：" + buttonType + ",可选值 " + ACTION_BUTTON_TYPES);
            }
        }
    }

    private void requireText(Map<String, Object> source, String key, String where) {
        Object value = source.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, where + " 缺少必填字段 " + key);
        }
    }

    /**
     * 校验弹窗表单配置 form_json 结构:整体为 JSON 对象且 fields 为数组;
     * 每个字段有 field/label/type(type 取值合法)、showOn/disabledOn 只能含 create/edit、
     * type=dict-select 必须带 dictType、type=select 的 options/optionsLoader 二选一。
     * form_json 为空(null/空白)表示未配置表单,不校验。
     */
    private void validateForm(String formJson) {
        if (formJson == null || formJson.isBlank()) {
            return;
        }
        Map<String, Object> form;
        try {
            Object parsed = objectMapper.readValue(formJson, new TypeReference<>() {
            });
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "表单配置 formJson 必须是 JSON 对象,如 {\"fields\":[...]}");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> formMap = (Map<String, Object>) map;
            form = formMap;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "表单配置 formJson 不是合法的 JSON:" + exception.getOriginalMessage());
        }
        Object fields = form.get("fields");
        if (!(fields instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "表单配置 formJson 的 fields 必须是非空数组");
        }
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?> itemMap)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "表单配置 fields[" + i + "] 必须是 JSON 对象");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> field = (Map<String, Object>) itemMap;
            String where = "表单配置[" + i + "]";
            requireText(field, "field", where);
            requireText(field, "label", where);
            String type = optionalText(field, "type");
            if (type == null || !FORM_FIELD_TYPES.contains(type)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    where + " 缺少必填字段 type,可选值 " + FORM_FIELD_TYPES);
            }
            validateModeList(field.get("showOn"), where + " 的 showOn");
            validateModeList(field.get("disabledOn"), where + " 的 disabledOn");
            if ("dict-select".equals(type) && optionalText(field, "dictType") == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, where + " 的 type=dict-select 时必须提供 dictType");
            }
            if ("select".equals(type) && optionalText(field, "dictType") == null
                && !field.containsKey("options") && optionalText(field, "optionsLoader") == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    where + " 的 type=select 必须提供 options/optionsLoader 之一");
            }
        }
    }

    /**
     * 校验页面壳配置 page_json:整体必须是合法的 JSON 对象。
     * 字段未配置/配置为空(null/空白)表示未配置壳,前端回退默认壳,不校验。
     * 字段级取值不强校验(避免与前端解析器的容错语义冲突),仅要求结构合法。
     */
    private void validatePageShell(String pageJson) {
        if (pageJson == null || pageJson.isBlank()) {
            return;
        }
        parseObject(pageJson, "页面壳配置 pageJson");
    }

    /**
     * 校验弹窗壳配置 dialog_json:整体必须是合法的 JSON 对象。
     * 为空(null/空白)表示未配置弹窗壳,前端回退默认弹窗,不校验。
     */
    private void validateDialogShell(String dialogJson) {
        if (dialogJson == null || dialogJson.isBlank()) {
            return;
        }
        parseObject(dialogJson, "弹窗壳配置 dialogJson");
    }

    private Map<String, Object> parseObject(String json, String name) {
        try {
            Object parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, name + " 必须是 JSON 对象,如 {\"title\":\"系统参数\"}");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> mapValue = (Map<String, Object>) map;
            return mapValue;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                name + " 不是合法的 JSON:" + exception.getOriginalMessage());
        }
    }

    private void validateModeList(Object value, String where) {
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> list)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, where + " 必须是数组,如 [\"create\"]");
        }
        for (Object item : list) {
            if (!(item instanceof String mode) || !FORM_MODES.contains(mode)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, where + " 只能包含 create/edit");
            }
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
            entity.getActionsJson(),
            entity.getFormJson(),
            entity.getPageJson(),
            entity.getDialogJson(),
            entity.getStatus(),
            entity.getRemark()
        );
    }
}
