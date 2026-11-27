package com.cornerstone.module.education.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.message.domain.entity.MsgEventEntity;
import com.cornerstone.module.education.message.domain.entity.MsgEventTypeEntity;
import com.cornerstone.module.education.message.domain.entity.MsgSourceEntity;
import com.cornerstone.module.education.message.mapper.MsgEventMapper;
import com.cornerstone.module.education.message.mapper.MsgEventReadMapper;
import com.cornerstone.module.education.message.mapper.MsgEventTypeMapper;
import com.cornerstone.module.education.message.mapper.MsgSourceMapper;
import com.cornerstone.module.education.message.support.MessageCondition;
import com.cornerstone.module.education.message.support.MessageConditionEvaluator;
import com.cornerstone.module.education.message.support.MessageRowValues;
import com.cornerstone.module.education.message.support.MessageTemplateRenderer;
import com.cornerstone.module.education.message.support.MessageTypeRules;
import com.cornerstone.module.education.message.support.MessageViewQueryExecutor;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 配置化巡检执行器：视图候选行 + 白名单条件 + 模板渲染 → 事件账本 upsert / 自动消解。
 *
 * <p>不加 {@code @Transactional}：整轮巡检是幂等的，逐行自动提交；中途异常时已写入的行
 * 下一轮会重新命中，且不会执行自动消解。失败原因必须能落库到 last_error，所以不能把
 * 「记录失败」和「抛异常回滚」放进同一个事务。</p>
 *
 * <p>自动消解只在「查询成功且未截断」时执行；查询异常或行数超限一律跳过消解，
 * 避免把没扫到的历史事件误标为已解决。</p>
 */
@Service
public class MessageEventScanService {

    private static final Logger log = LoggerFactory.getLogger(MessageEventScanService.class);

    private final MsgSourceMapper sourceMapper;
    private final MsgEventTypeMapper typeMapper;
    private final MsgEventMapper eventMapper;
    private final MsgEventReadMapper readMapper;
    private final MessageViewQueryExecutor queryExecutor;
    private final MessageConditionEvaluator conditionEvaluator;
    private final MessageTemplateRenderer templateRenderer;

    public MessageEventScanService(MsgSourceMapper sourceMapper,
                                   MsgEventTypeMapper typeMapper,
                                   MsgEventMapper eventMapper,
                                   MsgEventReadMapper readMapper,
                                   MessageViewQueryExecutor queryExecutor,
                                   MessageConditionEvaluator conditionEvaluator,
                                   MessageTemplateRenderer templateRenderer) {
        this.sourceMapper = sourceMapper;
        this.typeMapper = typeMapper;
        this.eventMapper = eventMapper;
        this.readMapper = readMapper;
        this.queryExecutor = queryExecutor;
        this.conditionEvaluator = conditionEvaluator;
        this.templateRenderer = templateRenderer;
    }

    public int scanType(MsgEventTypeEntity type, LocalDateTime now) {
        try {
            int matched = doScan(type, now);
            typeMapper.updateRunResult(type.getId(), now, matched, null);
            return matched;
        } catch (RuntimeException exception) {
            typeMapper.updateRunResult(type.getId(), now, 0, truncate(messageOf(exception)));
            throw exception;
        }
    }

    private int doScan(MsgEventTypeEntity type, LocalDateTime now) {
        if (!MessageTypeRules.SOURCE_MODE_SCAN.equals(type.getSourceMode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "事件类型 " + type.getTypeCode() + " 不是巡检类型，不能执行巡检");
        }
        MsgSourceEntity source = requireSource(type);
        MessageViewQueryExecutor.ScanResult result = queryExecutor.scan(source.getViewName());
        if (result.truncated()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "数据源 " + source.getSourceCode() + " 候选行超过 " + MessageViewQueryExecutor.MAX_ROWS
                    + " 行，本轮跳过自动消解；请收窄视图或提高巡检频率");
        }
        Set<String> columns = new LinkedHashSet<>(queryExecutor.columns(source.getViewName()));
        validateTemplates(type, columns);
        List<MessageCondition> conditions = conditionEvaluator.parse(type.getConditionJson());
        Set<String> seenKeys = new HashSet<>();
        int matched = 0;
        for (Map<String, Object> row : result.rows()) {
            if (!conditionEvaluator.matches(row, conditions)) {
                continue;
            }
            Long institutionId = MessageRowValues.asLong(row, "institution_id");
            if (institutionId == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "数据源 " + source.getSourceCode() + " 未返回 institution_id，无法按租户落库");
            }
            String dedupeKey = templateRenderer.render(type.getDedupeTemplate(), row).trim();
            if (dedupeKey.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "事件类型 " + type.getTypeCode() + " 的去重键渲染为空，无法保证幂等");
            }
            if (dedupeKey.length() > 300) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "事件类型 " + type.getTypeCode() + " 的去重键超过 300 字符，请缩短模板");
            }
            if (!seenKeys.add(institutionId + "|" + dedupeKey)) {
                // 同轮重复候选（如多表 join 放大）只保留第一条，避免重复计数
                continue;
            }
            String title = templateRenderer.render(type.getTitleTemplate(), row).trim();
            if (title.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "事件类型 " + type.getTypeCode() + " 的标题渲染为空");
            }
            String body = templateRenderer.render(type.getBodyTemplate(), row).trim();
            String actionUrl = MessageTypeRules.sanitizeActionUrl(
                templateRenderer.render(type.getActionTemplate(), row));
            upsertEvent(type, row, institutionId, dedupeKey, title, body, actionUrl, now);
            matched++;
        }
        resolveMissing(type, seenKeys, now);
        return matched;
    }

    private MsgSourceEntity requireSource(MsgEventTypeEntity type) {
        if (type.getSourceCode() == null || type.getSourceCode().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "事件类型 " + type.getTypeCode() + " 未配置数据源");
        }
        MsgSourceEntity source = sourceMapper.selectOne(new LambdaQueryWrapper<MsgSourceEntity>()
            .eq(MsgSourceEntity::getSourceCode, type.getSourceCode())
            .eq(MsgSourceEntity::getEnabled, true)
            .last("LIMIT 1"));
        if (source == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "事件类型 " + type.getTypeCode() + " 的数据源不存在或未启用：" + type.getSourceCode());
        }
        return source;
    }

    private void validateTemplates(MsgEventTypeEntity type, Set<String> columns) {
        templateRenderer.validateColumns(type.getTitleTemplate(), columns, "标题模板");
        templateRenderer.validateColumns(type.getBodyTemplate(), columns, "正文模板");
        templateRenderer.validateColumns(type.getActionTemplate(), columns, "深链模板");
        templateRenderer.validateColumns(type.getDedupeTemplate(), columns, "去重键模板");
    }

    private void upsertEvent(MsgEventTypeEntity type,
                             Map<String, Object> row,
                             Long institutionId,
                             String dedupeKey,
                             String title,
                             String body,
                             String actionUrl,
                             LocalDateTime now) {
        Long campusId = MessageRowValues.asLong(row, "campus_id");
        Long classId = MessageRowValues.asLong(row, "class_id");
        Long studentId = MessageRowValues.asLong(row, "student_id");
        Long teacherId = MessageRowValues.asLong(row, "teacher_id");
        Long lessonId = MessageRowValues.asLong(row, "lesson_id");
        String refType = MessageRowValues.asString(row, "ref_type");
        Long refId = MessageRowValues.asLong(row, "ref_id");

        MsgEventEntity existing = eventMapper.selectOne(new LambdaQueryWrapper<MsgEventEntity>()
            .eq(MsgEventEntity::getInstitutionId, institutionId)
            .eq(MsgEventEntity::getTypeCode, type.getTypeCode())
            .eq(MsgEventEntity::getDedupeKey, dedupeKey)
            .last("LIMIT 1"));
        if (existing == null) {
            MsgEventEntity event = new MsgEventEntity();
            event.setInstitutionId(institutionId);
            event.setCampusId(campusId);
            event.setTypeCode(type.getTypeCode());
            event.setCategory(type.getCategory());
            event.setLevel(type.getLevel());
            event.setTitle(title);
            event.setBody(body);
            event.setStatus(MessageTypeRules.STATUS_ACTIVE);
            event.setAnchorClassId(classId);
            event.setAnchorStudentId(studentId);
            event.setAnchorTeacherId(teacherId);
            event.setAnchorLessonId(lessonId);
            event.setRefType(refType);
            event.setRefId(refId);
            event.setDedupeKey(dedupeKey);
            event.setOccurredAt(now);
            event.setActionUrl(actionUrl);
            eventMapper.insert(event);
            return;
        }

        boolean reopening = MessageTypeRules.isResolvedStatus(existing.getStatus());
        LocalDateTime occurredAt = reopening || existing.getOccurredAt() == null
            ? now
            : existing.getOccurredAt();
        eventMapper.update(null, new LambdaUpdateWrapper<MsgEventEntity>()
            .eq(MsgEventEntity::getId, existing.getId())
            .set(MsgEventEntity::getCampusId, campusId)
            .set(MsgEventEntity::getCategory, type.getCategory())
            .set(MsgEventEntity::getLevel, type.getLevel())
            .set(MsgEventEntity::getTitle, title)
            .set(MsgEventEntity::getBody, body)
            .set(MsgEventEntity::getStatus, MessageTypeRules.STATUS_ACTIVE)
            .set(MsgEventEntity::getAnchorClassId, classId)
            .set(MsgEventEntity::getAnchorStudentId, studentId)
            .set(MsgEventEntity::getAnchorTeacherId, teacherId)
            .set(MsgEventEntity::getAnchorLessonId, lessonId)
            .set(MsgEventEntity::getRefType, refType)
            .set(MsgEventEntity::getRefId, refId)
            .set(MsgEventEntity::getOccurredAt, occurredAt)
            .set(MsgEventEntity::getActionUrl, actionUrl)
            .set(MsgEventEntity::getResolvedAt, null)
            .set(MsgEventEntity::getResolvedBy, null));
        if (reopening) {
            // 问题消解后又出现 = 新的一轮提醒：清掉原已读，让原先读过的人重新看到未读。
            // 必须物理删：唯一键 (user_id, event_id, deleted) 每个键只容得下一条墓碑。
            readMapper.physicalDeleteByEventIds(List.of(existing.getId()));
        }
    }

    /** 本轮未命中的 active 事件自动 resolved；截断/异常路径不会走到这里。 */
    private void resolveMissing(MsgEventTypeEntity type, Set<String> seenKeys, LocalDateTime now) {
        List<MsgEventEntity> activeEvents = eventMapper.selectList(new LambdaQueryWrapper<MsgEventEntity>()
            .eq(MsgEventEntity::getTypeCode, type.getTypeCode())
            .eq(MsgEventEntity::getStatus, MessageTypeRules.STATUS_ACTIVE)
            .select(MsgEventEntity::getId, MsgEventEntity::getInstitutionId, MsgEventEntity::getDedupeKey));
        for (MsgEventEntity event : activeEvents) {
            if (seenKeys.contains(event.getInstitutionId() + "|" + event.getDedupeKey())) {
                continue;
            }
            eventMapper.update(null, new LambdaUpdateWrapper<MsgEventEntity>()
                .eq(MsgEventEntity::getId, event.getId())
                .set(MsgEventEntity::getStatus, MessageTypeRules.STATUS_RESOLVED)
                .set(MsgEventEntity::getResolvedAt, now)
                .set(MsgEventEntity::getResolvedBy, null));
            log.debug("事件条件已消失，自动消解：type={}, id={}", type.getTypeCode(), event.getId());
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private String messageOf(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
