package com.cornerstone.module.education.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.PageSortSupport;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.message.domain.entity.MsgEventEntity;
import com.cornerstone.module.education.message.domain.entity.MsgEventLogEntity;
import com.cornerstone.module.education.message.domain.entity.MsgEventReadEntity;
import com.cornerstone.module.education.message.domain.entity.MsgEventTypeEntity;
import com.cornerstone.module.education.message.domain.entity.MsgSubscriptionEntity;
import com.cornerstone.module.education.message.dto.MessageAssigneeView;
import com.cornerstone.module.education.message.dto.MessageEventDetailView;
import com.cornerstone.module.education.message.dto.MessageEventPageQuery;
import com.cornerstone.module.education.message.dto.MessageEventView;
import com.cornerstone.module.education.message.dto.MessageManualCreateRequest;
import com.cornerstone.module.education.message.dto.MessageManualUpdateRequest;
import com.cornerstone.module.education.message.dto.MessageNotifyRequest;
import com.cornerstone.module.education.message.dto.MessageTimelineView;
import com.cornerstone.module.education.message.dto.MessageTypeOptionView;
import com.cornerstone.module.education.message.mapper.MsgEventLogMapper;
import com.cornerstone.module.education.message.mapper.MsgEventMapper;
import com.cornerstone.module.education.message.mapper.MsgEventReadMapper;
import com.cornerstone.module.education.message.mapper.MsgEventTypeMapper;
import com.cornerstone.module.education.message.mapper.MsgSubscriptionMapper;
import com.cornerstone.module.education.message.service.MessageEventService;
import com.cornerstone.module.education.message.support.MessageTypeRules;
import com.cornerstone.module.education.message.support.MessageVisibilitySupport;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageEventServiceImpl implements MessageEventService {

    private static final String NUDGE_TYPE_CODE = "manual_nudge";

    /** 事件列表可排序字段；未传时按发生时间倒序。 */
    private static final Map<String, SFunction<MsgEventEntity, ?>> SORTABLE = Map.of(
        "occurredAt", MsgEventEntity::getOccurredAt,
        "createdAt", MsgEventEntity::getCreatedAt,
        "level", MsgEventEntity::getLevel,
        "status", MsgEventEntity::getStatus,
        "typeCode", MsgEventEntity::getTypeCode);

    private final MsgEventMapper eventMapper;
    private final MsgEventReadMapper readMapper;
    private final MsgEventTypeMapper typeMapper;
    private final MsgEventLogMapper logMapper;
    private final MsgSubscriptionMapper subscriptionMapper;
    private final UserAccountMapper userAccountMapper;
    private final TeachingClassMapper classMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final CampusMapper campusMapper;
    private final MessageVisibilitySupport visibilitySupport;

    public MessageEventServiceImpl(MsgEventMapper eventMapper,
                                   MsgEventReadMapper readMapper,
                                   MsgEventTypeMapper typeMapper,
                                   MsgEventLogMapper logMapper,
                                   MsgSubscriptionMapper subscriptionMapper,
                                   UserAccountMapper userAccountMapper,
                                   TeachingClassMapper classMapper,
                                   StudentMapper studentMapper,
                                   TeacherMapper teacherMapper,
                                   CampusMapper campusMapper,
                                   MessageVisibilitySupport visibilitySupport) {
        this.eventMapper = eventMapper;
        this.readMapper = readMapper;
        this.typeMapper = typeMapper;
        this.logMapper = logMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.userAccountMapper = userAccountMapper;
        this.classMapper = classMapper;
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.campusMapper = campusMapper;
        this.visibilitySupport = visibilitySupport;
    }

    @Override
    public PageResult<MessageEventView> pageStream(MessageEventPageQuery query) {
        return page(query, false);
    }

    @Override
    public PageResult<MessageEventView> pageMine(MessageEventPageQuery query) {
        return page(query, true);
    }

    private PageResult<MessageEventView> page(MessageEventPageQuery query, boolean forceMine) {
        AuthenticatedUser user = AuthContext.requireUser();
        LambdaQueryWrapper<MsgEventEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgEventEntity::getInstitutionId, user.orgId());
        applyFilters(wrapper, query);
        visibilitySupport.applyCampusScope(wrapper);
        visibilitySupport.applyPortalScope(wrapper, user);
        if (forceMine || Boolean.TRUE.equals(query.getMine())) {
            applyMine(wrapper, user.userId());
        }
        if (Boolean.TRUE.equals(query.getUnreadOnly())) {
            applyUnread(wrapper, user.userId());
        }
        if (!PageSortSupport.apply(wrapper, query, SORTABLE, MsgEventEntity::getId)) {
            wrapper.orderByDesc(MsgEventEntity::getOccurredAt).orderByDesc(MsgEventEntity::getId);
        }
        Page<MsgEventEntity> page = eventMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, toViews(page.getRecords(), user.userId()));
    }

    private void applyFilters(LambdaQueryWrapper<MsgEventEntity> wrapper, MessageEventPageQuery query) {
        if (hasText(query.getTypeCode())) {
            wrapper.eq(MsgEventEntity::getTypeCode, query.getTypeCode().trim());
        }
        if (hasText(query.getCategory())) {
            wrapper.eq(MsgEventEntity::getCategory, query.getCategory().trim());
        }
        if (query.getCampusId() != null) {
            wrapper.eq(MsgEventEntity::getCampusId, query.getCampusId());
        }
        if (query.getClassId() != null) {
            wrapper.eq(MsgEventEntity::getAnchorClassId, query.getClassId());
        }
        if (query.getStudentId() != null) {
            wrapper.eq(MsgEventEntity::getAnchorStudentId, query.getStudentId());
        }
        if (query.getTeacherId() != null) {
            wrapper.eq(MsgEventEntity::getAnchorTeacherId, query.getTeacherId());
        }
        if (query.getAssigneeUserId() != null) {
            wrapper.eq(MsgEventEntity::getAssigneeUserId, query.getAssigneeUserId());
        }
        if (hasText(query.getStatus()) && !"all".equalsIgnoreCase(query.getStatus().trim())) {
            wrapper.eq(MsgEventEntity::getStatus, query.getStatus().trim());
        } else if (Boolean.TRUE.equals(query.getOpenOnly())) {
            wrapper.in(MsgEventEntity::getStatus, List.of(
                MessageTypeRules.STATUS_ACTIVE,
                MessageTypeRules.STATUS_OPEN,
                MessageTypeRules.STATUS_IN_PROGRESS));
        }
        if (hasText(query.getLevel())) {
            wrapper.eq(MsgEventEntity::getLevel, query.getLevel().trim());
        }
        if (hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(condition -> condition
                .like(MsgEventEntity::getTitle, keyword)
                .or()
                .like(MsgEventEntity::getBody, keyword));
        }
        if (query.getDateFrom() != null) {
            wrapper.ge(MsgEventEntity::getOccurredAt, query.getDateFrom().atStartOfDay());
        }
        if (query.getDateTo() != null) {
            LocalDate end = query.getDateTo();
            wrapper.lt(MsgEventEntity::getOccurredAt, end.plusDays(1).atStartOfDay());
        }
    }

    /**
     * 「我的」= 命中我的订阅规则 或 指派给我。
     *
     * <p>没有任何订阅规则时不做收窄（等于全部可见事件），对应「先都能看，再自己取」的口径；
     * 漏勾选不会让人彻底看不到，只是不在「我的」里。</p>
     */
    private void applyMine(LambdaQueryWrapper<MsgEventEntity> wrapper, Long userId) {
        List<MsgSubscriptionEntity> rules = subscriptionMapper.selectList(
            new LambdaQueryWrapper<MsgSubscriptionEntity>()
                .eq(MsgSubscriptionEntity::getUserId, userId)
                .eq(MsgSubscriptionEntity::getEnabled, true));
        if (rules.isEmpty()) {
            return;
        }
        Map<String, List<String>> grouped = rules.stream()
            .collect(Collectors.groupingBy(MsgSubscriptionEntity::getRuleType,
                LinkedHashMap::new,
                Collectors.mapping(MsgSubscriptionEntity::getRuleValue, Collectors.toList())));
        wrapper.and(outer -> {
            outer.nested(inner -> applyRules(inner, grouped));
            outer.or().eq(MsgEventEntity::getAssigneeUserId, userId);
        });
    }

    /** 同维度 OR、跨维度 AND；关键字维度内每条关键字匹配标题或正文。 */
    private void applyRules(LambdaQueryWrapper<MsgEventEntity> inner, Map<String, List<String>> grouped) {
        grouped.forEach((ruleType, values) -> {
            switch (ruleType) {
                case "type_code" -> inner.in(MsgEventEntity::getTypeCode, values);
                case "category" -> inner.in(MsgEventEntity::getCategory, values);
                case "level" -> inner.in(MsgEventEntity::getLevel, values);
                case "campus" -> applyLongIn(inner, MsgEventEntity::getCampusId, values);
                case "class" -> applyLongIn(inner, MsgEventEntity::getAnchorClassId, values);
                case "student" -> applyLongIn(inner, MsgEventEntity::getAnchorStudentId, values);
                case "teacher" -> applyLongIn(inner, MsgEventEntity::getAnchorTeacherId, values);
                case "keyword" -> inner.and(keywordWrapper -> {
                    boolean first = true;
                    for (String keyword : values) {
                        if (first) {
                            keywordWrapper.like(MsgEventEntity::getTitle, keyword)
                                .or()
                                .like(MsgEventEntity::getBody, keyword);
                            first = false;
                        } else {
                            keywordWrapper.or()
                                .like(MsgEventEntity::getTitle, keyword)
                                .or()
                                .like(MsgEventEntity::getBody, keyword);
                        }
                    }
                });
                default -> {
                    // 保存时已校验维度；历史脏数据忽略，不让一条坏规则拖垮整个「我的」
                }
            }
        });
    }

    private void applyLongIn(LambdaQueryWrapper<MsgEventEntity> wrapper,
                             SFunction<MsgEventEntity, Long> column,
                             Collection<String> values) {
        List<Long> ids = values.stream()
            .map(this::parseLong)
            .filter(Objects::nonNull)
            .toList();
        if (ids.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in(column, ids);
    }

    private void applyUnread(LambdaQueryWrapper<MsgEventEntity> wrapper, Long userId) {
        wrapper.apply("NOT EXISTS (SELECT 1 FROM jp_msg_event_read r "
            + "WHERE r.event_id = jp_msg_event.id AND r.user_id = {0} AND r.deleted = 0)", userId);
    }

    @Override
    public long countUnread() {
        AuthenticatedUser user = AuthContext.requireUser();
        LambdaQueryWrapper<MsgEventEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgEventEntity::getInstitutionId, user.orgId())
            .in(MsgEventEntity::getStatus, List.of(
                MessageTypeRules.STATUS_ACTIVE,
                MessageTypeRules.STATUS_OPEN,
                MessageTypeRules.STATUS_IN_PROGRESS));
        visibilitySupport.applyCampusScope(wrapper);
        visibilitySupport.applyPortalScope(wrapper, user);
        applyUnread(wrapper, user.userId());
        return eventMapper.selectCount(wrapper);
    }

    @Override
    public MessageEventDetailView getDetail(Long id) {
        AuthenticatedUser user = AuthContext.requireUser();
        MsgEventEntity event = requireEvent(id);
        visibilitySupport.assertVisible(event);
        LocalDateTime readAt = markRead(event.getId(), user.userId());
        return new MessageEventDetailView(toView(event, readAt), timeline(event.getId()));
    }

    @Override
    @Transactional
    public MessageEventView createManual(MessageManualCreateRequest request) {
        AuthenticatedUser user = AuthContext.requireUser();
        MsgEventTypeEntity type = requireManualType(request.getTypeCode(), true);
        validateAnchors(user.orgId(), request.getCampusId(), request.getClassId(),
            request.getStudentId(), request.getTeacherId(), request.getAssigneeUserId());

        MsgEventEntity event = new MsgEventEntity();
        event.setInstitutionId(user.orgId());
        event.setCampusId(request.getCampusId());
        event.setTypeCode(type.getTypeCode());
        event.setCategory(type.getCategory());
        event.setLevel(hasText(request.getLevel()) ? request.getLevel().trim() : type.getLevel());
        event.setTitle(request.getTitle().trim());
        event.setBody(request.getBody());
        event.setStatus(MessageTypeRules.STATUS_OPEN);
        event.setAnchorClassId(request.getClassId());
        event.setAnchorStudentId(request.getStudentId());
        event.setAnchorTeacherId(request.getTeacherId());
        event.setDedupeKey("manual:" + UUID.randomUUID());
        event.setOccurredAt(request.getOccurredAt() == null ? LocalDateTime.now() : request.getOccurredAt());
        event.setAssigneeUserId(request.getAssigneeUserId());
        event.setDueAt(request.getDueAt());
        event.setActionUrl(requireActionUrl(request.getActionUrl()));
        event.setCreatedBy(user.userId());
        eventMapper.insert(event);
        appendLog(event, user.userId(), "create", "创建了" + type.getTypeName());
        return toView(event, null);
    }

    @Override
    @Transactional
    public MessageEventView updateManual(Long id, MessageManualUpdateRequest request) {
        AuthenticatedUser user = AuthContext.requireUser();
        MsgEventEntity event = requireEvent(id);
        visibilitySupport.assertVisible(event);
        MsgEventTypeEntity type = requireType(event.getTypeCode());
        if (!MessageTypeRules.SOURCE_MODE_MANUAL.equals(type.getSourceMode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "系统巡检事件由条件自动消解，不能手工改状态");
        }
        validateAnchors(user.orgId(), event.getCampusId(), event.getAnchorClassId(),
            event.getAnchorStudentId(), event.getAnchorTeacherId(), request.getAssigneeUserId());

        String oldStatus = event.getStatus();
        event.setTitle(request.getTitle().trim());
        event.setBody(request.getBody());
        if (hasText(request.getLevel())) {
            event.setLevel(request.getLevel().trim());
        }
        event.setAssigneeUserId(request.getAssigneeUserId());
        event.setDueAt(request.getDueAt());
        event.setActionUrl(requireActionUrl(request.getActionUrl()));
        event.setStatus(request.getStatus());
        if (MessageTypeRules.isResolvedStatus(request.getStatus())) {
            if (event.getResolvedAt() == null) {
                event.setResolvedAt(LocalDateTime.now());
                event.setResolvedBy(user.userId());
            }
        } else {
            event.setResolvedAt(null);
            event.setResolvedBy(null);
        }
        event.setUpdatedBy(user.userId());
        eventMapper.updateById(event);

        String content = "状态 " + oldStatus + " → " + request.getStatus();
        if (hasText(request.getComment())) {
            content = content + "；" + request.getComment().trim();
        }
        appendLog(event, user.userId(), "status", content);
        return toView(event, readAtOf(event.getId(), user.userId()));
    }

    @Override
    @Transactional
    public int notifyUsers(Long id, MessageNotifyRequest request) {
        AuthenticatedUser user = AuthContext.requireUser();
        MsgEventEntity target = requireEvent(id);
        visibilitySupport.assertVisible(target);
        MsgEventTypeEntity nudgeType = requireType(NUDGE_TYPE_CODE);
        if (nudgeType == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "事件目录缺少 manual_nudge 类型，无法发送提醒");
        }
        Set<Long> userIds = new LinkedHashSet<>(request.getUserIds());
        int created = 0;
        for (Long userId : userIds) {
            if (Objects.equals(userId, user.userId())) {
                continue;
            }
            UserAccountEntity account = userAccountMapper.selectById(userId);
            if (account == null || !Objects.equals(account.getInstitutionId(), user.orgId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "被提醒人不存在或不属于当前机构");
            }
            MsgEventEntity nudge = new MsgEventEntity();
            nudge.setInstitutionId(user.orgId());
            nudge.setCampusId(target.getCampusId());
            nudge.setTypeCode(nudgeType.getTypeCode());
            nudge.setCategory(nudgeType.getCategory());
            nudge.setLevel(nudgeType.getLevel());
            nudge.setTitle(hasText(nudgeType.getTitleTemplate())
                ? nudgeType.getTitleTemplate().trim() : "有人提醒你查看一条事项");
            nudge.setBody(nudgeBody(request.getNote(), target));
            nudge.setStatus(MessageTypeRules.STATUS_OPEN);
            nudge.setAnchorClassId(target.getAnchorClassId());
            nudge.setAnchorStudentId(target.getAnchorStudentId());
            nudge.setAnchorTeacherId(target.getAnchorTeacherId());
            nudge.setRefType("message");
            nudge.setRefId(target.getId());
            nudge.setDedupeKey("nudge:" + target.getId() + ":" + userId + ":" + UUID.randomUUID());
            nudge.setOccurredAt(LocalDateTime.now());
            nudge.setAssigneeUserId(userId);
            nudge.setActionUrl("/messages?eventId=" + target.getId());
            nudge.setCreatedBy(user.userId());
            eventMapper.insert(nudge);
            created++;
        }
        return created;
    }

    private String nudgeBody(String note, MsgEventEntity target) {
        StringBuilder body = new StringBuilder();
        if (hasText(note)) {
            body.append(note.trim()).append("\n\n");
        }
        body.append(target.getTitle()).append("\n").append(target.getBody());
        return body.length() <= 5000 ? body.toString() : body.substring(0, 5000);
    }

    private MsgEventTypeEntity requireManualType(String typeCode, boolean requireCreatable) {
        MsgEventTypeEntity type = requireType(typeCode);
        if (type == null || !Boolean.TRUE.equals(type.getEnabled())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "事件类型不存在或未启用：" + typeCode);
        }
        if (!MessageTypeRules.SOURCE_MODE_MANUAL.equals(type.getSourceMode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "系统巡检事件不能手工新建：" + typeCode);
        }
        if (requireCreatable && !Boolean.TRUE.equals(type.getCreatable())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该事件类型不允许手工新建：" + typeCode);
        }
        return type;
    }

    private MsgEventTypeEntity requireType(String typeCode) {
        if (!hasText(typeCode)) {
            return null;
        }
        return typeMapper.selectOne(new LambdaQueryWrapper<MsgEventTypeEntity>()
            .eq(MsgEventTypeEntity::getTypeCode, typeCode.trim())
            .last("LIMIT 1"));
    }

    private void validateAnchors(Long institutionId,
                                 Long campusId,
                                 Long classId,
                                 Long studentId,
                                 Long teacherId,
                                 Long assigneeUserId) {
        CampusScopeGuard.assertNullableCampus(campusId, "事件");
        if (campusId != null) {
            CampusEntity campus = campusMapper.selectById(campusId);
            if (campus == null || !Objects.equals(campus.getInstitutionId(), institutionId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "校区不存在或不属于当前机构");
            }
        }
        if (classId != null) {
            TeachingClassEntity teachingClass = classMapper.selectById(classId);
            if (teachingClass == null || !Objects.equals(teachingClass.getInstitutionId(), institutionId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "班级不存在或不属于当前机构");
            }
            CampusScopeGuard.assertCampus(teachingClass.getCampusId(), "班级");
        }
        if (studentId != null) {
            StudentEntity student = studentMapper.selectById(studentId);
            if (student == null || !Objects.equals(student.getInstitutionId(), institutionId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "学员不存在或不属于当前机构");
            }
        }
        if (teacherId != null) {
            TeacherEntity teacher = teacherMapper.selectById(teacherId);
            if (teacher == null || !Objects.equals(teacher.getInstitutionId(), institutionId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "教师不存在或不属于当前机构");
            }
            CampusScopeGuard.assertCampus(teacher.getCampusId(), "教师");
        }
        if (assigneeUserId != null) {
            UserAccountEntity assignee = userAccountMapper.selectById(assigneeUserId);
            if (assignee == null || !Objects.equals(assignee.getInstitutionId(), institutionId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "指派人不存在或不属于当前机构");
            }
        }
    }

    private String requireActionUrl(String actionUrl) {
        if (!hasText(actionUrl)) {
            return null;
        }
        String sanitized = MessageTypeRules.sanitizeActionUrl(actionUrl);
        if (sanitized == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "深链只允许本系统页面路径（/schedule、/fee-ledger、/messages 等）");
        }
        return sanitized;
    }

    private MsgEventEntity requireEvent(Long id) {
        MsgEventEntity event = id == null ? null : eventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "事件不存在或已不可见");
        }
        return event;
    }

    private LocalDateTime markRead(Long eventId, Long userId) {
        LocalDateTime readAt = readAtOf(eventId, userId);
        if (readAt != null) {
            return readAt;
        }
        MsgEventReadEntity read = new MsgEventReadEntity();
        read.setUserId(userId);
        read.setEventId(eventId);
        read.setReadAt(LocalDateTime.now());
        try {
            readMapper.insert(read);
            return read.getReadAt();
        } catch (DuplicateKeyException exception) {
            // 并发打开同一条事件：以先落库的那次时间为准
            LocalDateTime existing = readAtOf(eventId, userId);
            return existing == null ? read.getReadAt() : existing;
        }
    }

    private LocalDateTime readAtOf(Long eventId, Long userId) {
        MsgEventReadEntity read = readMapper.selectOne(new LambdaQueryWrapper<MsgEventReadEntity>()
            .eq(MsgEventReadEntity::getEventId, eventId)
            .eq(MsgEventReadEntity::getUserId, userId)
            .last("LIMIT 1"));
        return read == null ? null : read.getReadAt();
    }

    private void appendLog(MsgEventEntity event, Long userId, String action, String content) {
        MsgEventLogEntity log = new MsgEventLogEntity();
        log.setInstitutionId(event.getInstitutionId());
        log.setEventId(event.getId());
        log.setUserId(userId);
        log.setAction(action);
        log.setContent(content.length() <= 1000 ? content : content.substring(0, 1000));
        log.setCreatedBy(userId);
        logMapper.insert(log);
    }

    private List<MessageTimelineView> timeline(Long eventId) {
        List<MsgEventLogEntity> logs = logMapper.selectList(new LambdaQueryWrapper<MsgEventLogEntity>()
            .eq(MsgEventLogEntity::getEventId, eventId)
            .orderByAsc(MsgEventLogEntity::getCreatedAt)
            .orderByAsc(MsgEventLogEntity::getId));
        Map<Long, String> names = userNames(logs.stream().map(MsgEventLogEntity::getUserId).toList());
        return logs.stream()
            .map(log -> new MessageTimelineView(
                log.getId(), log.getAction(), log.getContent(), log.getUserId(),
                names.get(log.getUserId()), log.getCreatedAt()))
            .toList();
    }

    private List<MessageEventView> toViews(List<MsgEventEntity> events, Long userId) {
        if (events.isEmpty()) {
            return List.of();
        }
        Map<String, String> typeNames = typeNames();
        List<Long> userIds = new ArrayList<>();
        events.forEach(event -> {
            userIds.add(event.getAssigneeUserId());
            userIds.add(event.getCreatedBy());
            userIds.add(event.getResolvedBy());
        });
        Map<Long, String> names = userNames(userIds);
        List<Long> eventIds = events.stream().map(MsgEventEntity::getId).toList();
        Map<Long, LocalDateTime> reads = readMapper.selectList(new LambdaQueryWrapper<MsgEventReadEntity>()
                .eq(MsgEventReadEntity::getUserId, userId)
                .in(MsgEventReadEntity::getEventId, eventIds))
            .stream()
            .collect(Collectors.toMap(MsgEventReadEntity::getEventId, MsgEventReadEntity::getReadAt,
                (first, second) -> first));
        return events.stream()
            .map(event -> toView(event, typeNames, names, reads.get(event.getId())))
            .toList();
    }

    private MessageEventView toView(MsgEventEntity event, LocalDateTime readAt) {
        Map<String, String> typeNames = typeNames();
        Map<Long, String> names = userNames(java.util.Arrays.asList(
            event.getAssigneeUserId(), event.getCreatedBy(), event.getResolvedBy()));
        return toView(event, typeNames, names, readAt);
    }

    private MessageEventView toView(MsgEventEntity event,
                                    Map<String, String> typeNames,
                                    Map<Long, String> names,
                                    LocalDateTime readAt) {
        return new MessageEventView(
            event.getId(),
            event.getInstitutionId(),
            event.getCampusId(),
            event.getTypeCode(),
            typeNames.getOrDefault(event.getTypeCode(), event.getTypeCode()),
            event.getCategory(),
            event.getLevel(),
            event.getTitle(),
            event.getBody(),
            event.getStatus(),
            event.getAnchorClassId(),
            event.getAnchorStudentId(),
            event.getAnchorTeacherId(),
            event.getAnchorLessonId(),
            event.getRefType(),
            event.getRefId(),
            event.getOccurredAt(),
            event.getExpireAt(),
            event.getAssigneeUserId(),
            nameOf(names, event.getAssigneeUserId()),
            event.getDueAt(),
            event.getActionUrl(),
            readAt,
            readAt != null,
            event.getResolvedAt(),
            event.getResolvedBy(),
            nameOf(names, event.getResolvedBy()),
            event.getCreatedAt(),
            event.getCreatedBy(),
            nameOf(names, event.getCreatedBy()));
    }

    /**
     * 按账号 ID 取名。ID 为空必须直接返回 null。
     *
     * <p>不能直接 {@code names.get(null)}：没有任何账号可查时 {@link #userNames} 返回
     * {@code Map.of()}，JDK 不可变 Map 的 get(null) 会抛 NPE（巡检事件恰好三个账号列全空，
     * 打开事件中心就 500）。</p>
     */
    private static String nameOf(Map<Long, String> names, Long userId) {
        return userId == null ? null : names.get(userId);
    }

    private Map<String, String> typeNames() {
        return typeMapper.selectList(null).stream()
            .collect(Collectors.toMap(MsgEventTypeEntity::getTypeCode,
                type -> type.getTypeName() == null ? type.getTypeCode() : type.getTypeName(),
                (first, second) -> first));
    }

    private Map<Long, String> userNames(Collection<Long> userIds) {
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userAccountMapper.selectBatchIds(ids).stream()
            .filter(account -> account.getDisplayName() != null)
            .collect(Collectors.toMap(UserAccountEntity::getId, UserAccountEntity::getDisplayName,
                (first, second) -> first));
    }

    @Override
    public List<MessageTypeOptionView> listTypeOptions() {
        return typeMapper.selectList(new LambdaQueryWrapper<MsgEventTypeEntity>()
                .eq(MsgEventTypeEntity::getEnabled, true)
                .orderByAsc(MsgEventTypeEntity::getSortNo)
                .orderByAsc(MsgEventTypeEntity::getId))
            .stream()
            .map(type -> new MessageTypeOptionView(
                type.getTypeCode(), type.getTypeName(), type.getCategory(),
                type.getLevel(), type.getSourceMode(), type.getCreatable()))
            .toList();
    }

    @Override
    public List<MessageAssigneeView> listAssignees(String keyword) {
        AuthenticatedUser user = AuthContext.requireUser();
        LambdaQueryWrapper<UserAccountEntity> wrapper = new LambdaQueryWrapper<UserAccountEntity>()
            .eq(UserAccountEntity::getInstitutionId, user.orgId())
            .eq(UserAccountEntity::getStatus, "active")
            .orderByAsc(UserAccountEntity::getDisplayName)
            .last("LIMIT 200");
        if (hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(condition -> condition
                .like(UserAccountEntity::getDisplayName, trimmed)
                .or()
                .like(UserAccountEntity::getUsername, trimmed));
        }
        return userAccountMapper.selectList(wrapper).stream()
            // 家长/学生自助注册的账号不在后台指派范围里；register_source 可能为 NULL，按正向排除
            .filter(account -> !"WECHAT_MINIAPP".equals(account.getRegisterSource()))
            .limit(50)
            .map(account -> new MessageAssigneeView(
                account.getId(), account.getDisplayName(), account.getUsername()))
            .toList();
    }

    private Long parseLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
