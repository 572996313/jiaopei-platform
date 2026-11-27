package com.cornerstone.module.education.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.message.domain.entity.MsgEventEntity;
import com.cornerstone.module.education.message.domain.entity.MsgEventTypeEntity;
import com.cornerstone.module.education.message.domain.entity.MsgSourceEntity;
import com.cornerstone.module.education.message.domain.entity.MsgSubscriptionEntity;
import com.cornerstone.module.education.message.dto.MessageEventTypeUpsertRequest;
import com.cornerstone.module.education.message.dto.MessageEventTypeView;
import com.cornerstone.module.education.message.dto.MessageReadLedgerSummaryView;
import com.cornerstone.module.education.message.dto.MessageReaderView;
import com.cornerstone.module.education.message.dto.MessageSourceView;
import com.cornerstone.module.education.message.mapper.MsgEventMapper;
import com.cornerstone.module.education.message.mapper.MsgEventReadMapper;
import com.cornerstone.module.education.message.mapper.MsgEventTypeMapper;
import com.cornerstone.module.education.message.mapper.MsgSourceMapper;
import com.cornerstone.module.education.message.mapper.MsgSubscriptionMapper;
import com.cornerstone.module.education.message.support.MessageCondition;
import com.cornerstone.module.education.message.support.MessageConditionEvaluator;
import com.cornerstone.module.education.message.support.MessageTemplateRenderer;
import com.cornerstone.module.education.message.support.MessageTypeRules;
import com.cornerstone.module.education.message.support.MessageViewQueryExecutor;
import com.cornerstone.module.education.message.service.MessageEventTypeService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageEventTypeServiceImpl implements MessageEventTypeService {

    private static final Logger log = LoggerFactory.getLogger(MessageEventTypeServiceImpl.class);
    private static final String DICT_TYPE_CATEGORY = "message_category";

    private final MsgEventTypeMapper typeMapper;
    private final MsgSourceMapper sourceMapper;
    private final MsgEventMapper eventMapper;
    private final MsgEventReadMapper readMapper;
    private final MsgSubscriptionMapper subscriptionMapper;
    private final MessageViewQueryExecutor queryExecutor;
    private final MessageConditionEvaluator conditionEvaluator;
    private final MessageTemplateRenderer templateRenderer;
    private final DictValueValidator dictValueValidator;
    private final ObjectMapper objectMapper;

    public MessageEventTypeServiceImpl(MsgEventTypeMapper typeMapper,
                                       MsgSourceMapper sourceMapper,
                                       MsgEventMapper eventMapper,
                                       MsgEventReadMapper readMapper,
                                       MsgSubscriptionMapper subscriptionMapper,
                                       MessageViewQueryExecutor queryExecutor,
                                       MessageConditionEvaluator conditionEvaluator,
                                       MessageTemplateRenderer templateRenderer,
                                       DictValueValidator dictValueValidator,
                                       ObjectMapper objectMapper) {
        this.typeMapper = typeMapper;
        this.sourceMapper = sourceMapper;
        this.eventMapper = eventMapper;
        this.readMapper = readMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.queryExecutor = queryExecutor;
        this.conditionEvaluator = conditionEvaluator;
        this.templateRenderer = templateRenderer;
        this.dictValueValidator = dictValueValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MessageEventTypeView> listTypes() {
        Map<String, String> viewNames = sourceMapper.selectList(null).stream()
            .collect(java.util.stream.Collectors.toMap(MsgSourceEntity::getSourceCode,
                MsgSourceEntity::getViewName, (first, second) -> first));
        return typeMapper.selectList(new LambdaQueryWrapper<MsgEventTypeEntity>()
                .orderByAsc(MsgEventTypeEntity::getSortNo)
                .orderByAsc(MsgEventTypeEntity::getId))
            .stream()
            .map(type -> toView(type, viewNames.get(type.getSourceCode())))
            .toList();
    }

    @Override
    @Transactional
    public MessageEventTypeView createType(MessageEventTypeUpsertRequest request) {
        String typeCode = request.getTypeCode().trim();
        if (findType(typeCode) != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "事件类型码已存在：" + typeCode);
        }
        MsgEventTypeEntity type = new MsgEventTypeEntity();
        type.setTypeCode(typeCode);
        applyAndValidate(type, request);
        type.setCreatedBy(AuthContext.requireUser().userId());
        type.setUpdatedBy(type.getCreatedBy());
        typeMapper.insert(type);
        return toView(type, viewNameOf(type.getSourceCode()));
    }

    @Override
    @Transactional
    public MessageEventTypeView updateType(String typeCode, MessageEventTypeUpsertRequest request) {
        MsgEventTypeEntity type = findType(typeCode);
        if (type == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "事件类型不存在：" + typeCode);
        }
        if (request.getTypeCode() != null && !request.getTypeCode().isBlank()
            && !typeCode.equals(request.getTypeCode().trim())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "事件类型码创建后不可修改");
        }
        applyAndValidate(type, request);
        type.setUpdatedBy(AuthContext.requireUser().userId());
        typeMapper.updateById(type);
        return toView(type, viewNameOf(type.getSourceCode()));
    }

    private void applyAndValidate(MsgEventTypeEntity type, MessageEventTypeUpsertRequest request) {
        String category = dictValueValidator.validate(DICT_TYPE_CATEGORY, "分类", request.getCategory());
        if (category == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分类不能为空");
        }
        if (!MessageTypeRules.LEVELS.contains(request.getLevel())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "级别无效：" + request.getLevel());
        }
        if (!MessageTypeRules.VISIBILITIES.contains(request.getVisibility())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "可见端无效：" + request.getVisibility());
        }
        String sourceMode = request.getSourceMode();
        type.setTypeName(request.getTypeName().trim());
        type.setCategory(category);
        type.setSourceMode(sourceMode);
        type.setLevel(request.getLevel());
        type.setVisibility(request.getVisibility());
        type.setTitleTemplate(request.getTitleTemplate().trim());
        type.setBodyTemplate(request.getBodyTemplate() == null ? "" : request.getBodyTemplate());
        type.setActionTemplate(hasText(request.getActionTemplate())
            ? request.getActionTemplate().trim() : null);
        type.setDedupeTemplate(request.getDedupeTemplate() == null ? "" : request.getDedupeTemplate());
        type.setEnabled(!Boolean.FALSE.equals(request.getEnabled()));
        type.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());

        if (MessageTypeRules.SOURCE_MODE_SCAN.equals(sourceMode)) {
            applyScanType(type, request);
        } else {
            applyManualType(type, request);
        }
    }

    private void applyScanType(MsgEventTypeEntity type, MessageEventTypeUpsertRequest request) {
        if (!hasText(request.getSourceCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "巡检类型必须选择数据源");
        }
        MsgSourceEntity source = sourceMapper.selectOne(new LambdaQueryWrapper<MsgSourceEntity>()
            .eq(MsgSourceEntity::getSourceCode, request.getSourceCode().trim())
            .eq(MsgSourceEntity::getEnabled, true)
            .last("LIMIT 1"));
        if (source == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "数据源不存在或未启用：" + request.getSourceCode());
        }
        if (!hasText(type.getDedupeTemplate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "巡检类型必须配置去重键模板");
        }
        Set<String> columns = new LinkedHashSet<>(queryExecutor.columns(source.getViewName()));
        List<MessageCondition> conditions = request.getConditions() == null
            ? List.of() : request.getConditions();
        for (MessageCondition condition : conditions) {
            if (condition == null || condition.column() == null
                || columns.stream().noneMatch(column -> column.equalsIgnoreCase(condition.column()))) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "条件引用了视图中不存在的列：" + (condition == null ? "空条件" : condition.column()));
            }
        }
        // 借 parse 做比较符/数字值结构校验；序列化后落库
        String conditionJson = serializeConditions(conditions);
        conditionEvaluator.parse(conditionJson);
        templateRenderer.validateColumns(type.getTitleTemplate(), columns, "标题模板");
        templateRenderer.validateColumns(type.getBodyTemplate(), columns, "正文模板");
        templateRenderer.validateColumns(type.getActionTemplate(), columns, "深链模板");
        templateRenderer.validateColumns(type.getDedupeTemplate(), columns, "去重键模板");

        type.setSourceCode(source.getSourceCode());
        type.setConditionJson(conditionJson);
        type.setIntervalMinutes(request.getIntervalMinutes() == null ? 5 : request.getIntervalMinutes());
        type.setRetentionDays(request.getRetentionDays() == null ? 90 : request.getRetentionDays());
        type.setCreatable(false);
    }

    private void applyManualType(MsgEventTypeEntity type, MessageEventTypeUpsertRequest request) {
        type.setSourceCode(null);
        type.setConditionJson(null);
        type.setIntervalMinutes(1);
        type.setRetentionDays(request.getRetentionDays() == null ? 365 : request.getRetentionDays());
        type.setCreatable(!Boolean.FALSE.equals(request.getCreatable()));
        type.setLastRunAt(null);
        type.setLastRunCount(0);
        type.setLastError(null);
    }

    private String serializeConditions(List<MessageCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(conditions);
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "事件条件 JSON 序列化失败：" + exception.getMessage());
        }
    }

    private MsgEventTypeEntity findType(String typeCode) {
        if (!hasText(typeCode)) {
            return null;
        }
        return typeMapper.selectOne(new LambdaQueryWrapper<MsgEventTypeEntity>()
            .eq(MsgEventTypeEntity::getTypeCode, typeCode.trim())
            .last("LIMIT 1"));
    }

    private String viewNameOf(String sourceCode) {
        if (!hasText(sourceCode)) {
            return null;
        }
        MsgSourceEntity source = sourceMapper.selectOne(new LambdaQueryWrapper<MsgSourceEntity>()
            .eq(MsgSourceEntity::getSourceCode, sourceCode)
            .last("LIMIT 1"));
        return source == null ? null : source.getViewName();
    }

    private MessageEventTypeView toView(MsgEventTypeEntity type, String sourceViewName) {
        List<MessageCondition> conditions;
        try {
            conditions = conditionEvaluator.parse(type.getConditionJson());
        } catch (RuntimeException exception) {
            // 目录页不能因为一条坏配置整页打不开；具体错误在巡检的 last_error 里
            log.warn("事件类型条件无法解析：type={}, 原因={}", type.getTypeCode(), exception.getMessage());
            conditions = List.of();
        }
        return new MessageEventTypeView(
            type.getId(),
            type.getTypeCode(),
            type.getTypeName(),
            type.getCategory(),
            type.getSourceMode(),
            type.getSourceCode(),
            sourceViewName,
            conditions,
            type.getTitleTemplate(),
            type.getBodyTemplate(),
            type.getActionTemplate(),
            type.getDedupeTemplate(),
            type.getLevel(),
            type.getVisibility(),
            type.getIntervalMinutes(),
            type.getRetentionDays(),
            type.getCreatable(),
            type.getEnabled(),
            type.getLastRunAt(),
            type.getLastRunCount(),
            type.getLastError(),
            type.getSortNo());
    }

    @Override
    public List<MessageSourceView> listSources() {
        return sourceMapper.selectList(new LambdaQueryWrapper<MsgSourceEntity>()
                .orderByAsc(MsgSourceEntity::getSourceCode))
            .stream()
            .map(source -> new MessageSourceView(
                source.getId(), source.getSourceCode(), source.getDisplayName(), source.getViewName(),
                source.getDescription(), safeColumns(source.getViewName()), source.getEnabled(),
                source.getLastValidatedAt()))
            .toList();
    }

    @Override
    @Transactional
    public MessageSourceView validateSource(String sourceCode) {
        MsgSourceEntity source = sourceMapper.selectOne(new LambdaQueryWrapper<MsgSourceEntity>()
            .eq(MsgSourceEntity::getSourceCode, sourceCode)
            .last("LIMIT 1"));
        if (source == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据源不存在：" + sourceCode);
        }
        List<String> columns = queryExecutor.columns(source.getViewName());
        source.setColumnContract(String.join(",", columns));
        source.setLastValidatedAt(LocalDateTime.now());
        source.setUpdatedBy(AuthContext.requireUser().userId());
        sourceMapper.updateById(source);
        return new MessageSourceView(
            source.getId(), source.getSourceCode(), source.getDisplayName(), source.getViewName(),
            source.getDescription(), columns, source.getEnabled(), source.getLastValidatedAt());
    }

    private List<String> safeColumns(String viewName) {
        try {
            return queryExecutor.columns(viewName);
        } catch (RuntimeException exception) {
            log.warn("数据源列读取失败：view={}, 原因={}", viewName, exception.getMessage());
            return List.of();
        }
    }

    @Override
    public List<MessageReadLedgerSummaryView> readLedger() {
        List<MsgEventTypeEntity> types = typeMapper.selectList(new LambdaQueryWrapper<MsgEventTypeEntity>()
            .orderByAsc(MsgEventTypeEntity::getSortNo)
            .orderByAsc(MsgEventTypeEntity::getId));
        return types.stream().map(type -> {
            long total = eventMapper.selectCount(new LambdaQueryWrapper<MsgEventEntity>()
                .eq(MsgEventEntity::getTypeCode, type.getTypeCode()));
            long active = eventMapper.selectCount(new LambdaQueryWrapper<MsgEventEntity>()
                .eq(MsgEventEntity::getTypeCode, type.getTypeCode())
                .in(MsgEventEntity::getStatus, List.of(
                    MessageTypeRules.STATUS_ACTIVE,
                    MessageTypeRules.STATUS_OPEN,
                    MessageTypeRules.STATUS_IN_PROGRESS)));
            long readCount = readMapper.countDistinctUsersByTypeCode(type.getTypeCode());
            long subscribers = subscriptionMapper.selectCount(new LambdaQueryWrapper<MsgSubscriptionEntity>()
                .eq(MsgSubscriptionEntity::getRuleType, "type_code")
                .eq(MsgSubscriptionEntity::getRuleValue, type.getTypeCode())
                .eq(MsgSubscriptionEntity::getEnabled, true));
            return new MessageReadLedgerSummaryView(
                type.getTypeCode(), type.getTypeName(), type.getCategory(), type.getSourceMode(),
                total, active, readCount, subscribers,
                type.getLastRunAt(), type.getLastRunCount(), type.getLastError());
        }).toList();
    }

    @Override
    public List<MessageReaderView> readers(Long eventId) {
        MsgEventEntity event = eventMapper.selectById(eventId);
        if (event == null || !Objects.equals(event.getInstitutionId(), AuthContext.requireUser().orgId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "事件不存在");
        }
        return readMapper.listReaders(eventId).stream()
            .map(row -> new MessageReaderView(
                asLong(row.get("userId")),
                row.get("userName") == null ? "已删除账号" : String.valueOf(row.get("userName")),
                asDateTime(row.get("readAt"))))
            .toList();
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private LocalDateTime asDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
