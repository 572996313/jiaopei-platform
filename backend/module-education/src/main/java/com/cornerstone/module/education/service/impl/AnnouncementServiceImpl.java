package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.cornerstone.module.education.domain.entity.MsgAnnouncementEntity;
import com.cornerstone.module.education.domain.entity.MsgAnnouncementReceiverEntity;
import com.cornerstone.module.education.domain.entity.MsgAnnouncementTemplateEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.AnnouncementPageQuery;
import com.cornerstone.module.education.dto.AnnouncementRecipientPreview;
import com.cornerstone.module.education.dto.AnnouncementTemplateUpsertRequest;
import com.cornerstone.module.education.dto.AnnouncementTemplateView;
import com.cornerstone.module.education.dto.AnnouncementUpsertRequest;
import com.cornerstone.module.education.dto.AnnouncementView;
import com.cornerstone.module.education.dto.MyAnnouncementView;
import com.cornerstone.module.education.mapper.MsgAnnouncementMapper;
import com.cornerstone.module.education.mapper.MsgAnnouncementReceiverMapper;
import com.cornerstone.module.education.mapper.MsgAnnouncementTemplateMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.AnnouncementService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final String DICT_TYPE_ANNOUNCEMENT = "announcement_type";
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_RECALLED = "recalled";
    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_PART = "part";

    /** 公告列表可排序字段:已读人数是聚合值,不在本表列里,不参与排序 */
    private static final Map<String, SFunction<MsgAnnouncementEntity, ?>> ANNOUNCEMENT_SORTABLE = Map.of(
        "title", MsgAnnouncementEntity::getTitle,
        "typeCode", MsgAnnouncementEntity::getTypeCode,
        "status", MsgAnnouncementEntity::getStatus,
        "topFlag", MsgAnnouncementEntity::getTopFlag,
        "receiverTotal", MsgAnnouncementEntity::getReceiverTotal,
        "publishedAt", MsgAnnouncementEntity::getPublishedAt,
        "createdAt", MsgAnnouncementEntity::getCreatedAt);

    /**
     * 三档接收对象各自绑死一条解析 SQL 分支（教师/家长/全部账号），运维在字典里加一档不会多出解析逻辑，
     * 所以按 AGENTS「枚举值/字典规范」的边界留在代码里，不入字典。
     */
    private static final String AUDIENCE_TEACHER = "teacher";
    private static final String AUDIENCE_PARENT = "parent";
    private static final String AUDIENCE_ACCOUNT = "account";
    private static final List<String> AUDIENCE_VALUES = List.of(AUDIENCE_TEACHER, AUDIENCE_PARENT, AUDIENCE_ACCOUNT);

    /** campus_ids / class_ids 是 VARCHAR(1024) 的逗号快照，雪花 ID 19 位，约 85 个就会溢出。 */
    private static final int SCOPE_COLUMN_MAX_LENGTH = 1024;

    private final MsgAnnouncementMapper announcementMapper;
    private final MsgAnnouncementReceiverMapper receiverMapper;
    private final MsgAnnouncementTemplateMapper templateMapper;
    private final UserAccountMapper userAccountMapper;
    private final CampusMapper campusMapper;
    private final TeachingClassMapper classMapper;
    private final DictValueValidator dictValueValidator;

    public AnnouncementServiceImpl(MsgAnnouncementMapper announcementMapper,
                                   MsgAnnouncementReceiverMapper receiverMapper,
                                   MsgAnnouncementTemplateMapper templateMapper,
                                   UserAccountMapper userAccountMapper,
                                   CampusMapper campusMapper,
                                   TeachingClassMapper classMapper,
                                   DictValueValidator dictValueValidator) {
        this.announcementMapper = announcementMapper;
        this.receiverMapper = receiverMapper;
        this.templateMapper = templateMapper;
        this.userAccountMapper = userAccountMapper;
        this.campusMapper = campusMapper;
        this.classMapper = classMapper;
        this.dictValueValidator = dictValueValidator;
    }

    @Override
    public PageResult<AnnouncementView> pageAnnouncements(AnnouncementPageQuery query) {
        Long institutionId = AuthContext.requireUser().orgId();
        LambdaQueryWrapper<MsgAnnouncementEntity> wrapper = new LambdaQueryWrapper<MsgAnnouncementEntity>()
            .eq(MsgAnnouncementEntity::getInstitutionId, institutionId);
        if (hasText(query.getStatus())) {
            wrapper.eq(MsgAnnouncementEntity::getStatus, query.getStatus().trim());
        }
        if (hasText(query.getTypeCode())) {
            wrapper.eq(MsgAnnouncementEntity::getTypeCode, query.getTypeCode().trim());
        }
        if (hasText(query.getAudienceCode())) {
            // 接收对象存的是逗号快照，用 FIND_IN_SET 而不是 LIKE，避免 teacher 匹到 parent 这类子串误伤
            wrapper.apply("FIND_IN_SET({0}, audience_codes) > 0", query.getAudienceCode().trim());
        }
        if (hasText(query.getKeyword())) {
            wrapper.like(MsgAnnouncementEntity::getTitle, query.getKeyword().trim());
        }
        if (!PageSortSupport.apply(wrapper, query, ANNOUNCEMENT_SORTABLE, MsgAnnouncementEntity::getId)) {
            wrapper.orderByDesc(MsgAnnouncementEntity::getCreatedAt);
        }
        Page<MsgAnnouncementEntity> page = announcementMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        List<Long> ids = page.getRecords().stream().map(MsgAnnouncementEntity::getId).toList();
        Map<Long, Long> readCounts = readCountsOf(ids);
        Map<Long, String> names = displayNames(page.getRecords());
        return PageResult.of(page, page.getRecords().stream()
            .map(entity -> toView(entity, readCounts.getOrDefault(entity.getId(), 0L), names))
            .toList());
    }

    @Override
    public AnnouncementView getAnnouncement(Long id) {
        MsgAnnouncementEntity entity = requireOwnAnnouncement(id, AuthContext.requireUser().orgId());
        return toView(entity, readCountsOf(List.of(id)).getOrDefault(id, 0L), displayNames(List.of(entity)));
    }

    @Override
    public void requireShareable(Long id) {
        MsgAnnouncementEntity entity = requireOwnAnnouncement(id, AuthContext.requireUser().orgId());
        if (!STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "只有已发布的公告能生成小程序码：草稿还没有接收人，撤回后扫码也看不到内容");
        }
    }

    @Override
    public AnnouncementRecipientPreview previewRecipients(AnnouncementUpsertRequest request) {
        Long institutionId = AuthContext.requireUser().orgId();
        Range range = normalizeRange(institutionId, request);
        Map<Long, String> receivers = resolveReceivers(institutionId, range);
        int unbound = intOf(receiverMapper.countUnboundStudents(institutionId, range.campusIds(), range.classIds()));
        return new AnnouncementRecipientPreview(
            countByAudience(receivers, AUDIENCE_TEACHER),
            countByAudience(receivers, AUDIENCE_PARENT),
            countByAudience(receivers, AUDIENCE_ACCOUNT),
            receivers.size(),
            unbound,
            buildNotices(range, receivers, unbound));
    }

    @Override
    @Transactional
    public AnnouncementView createDraft(AnnouncementUpsertRequest request) {
        Long institutionId = AuthContext.requireUser().orgId();
        Range range = normalizeRange(institutionId, request);
        MsgAnnouncementEntity entity = new MsgAnnouncementEntity();
        entity.setInstitutionId(institutionId);
        applyDraftFields(entity, request, range);
        entity.setStatus(STATUS_DRAFT);
        entity.setReceiverTotal(0);
        announcementMapper.insert(entity);
        return toView(entity, 0L, displayNames(List.of(entity)));
    }

    @Override
    @Transactional
    public AnnouncementView updateDraft(Long id, AnnouncementUpsertRequest request) {
        MsgAnnouncementEntity entity = requireOwnAnnouncement(id, AuthContext.requireUser().orgId());
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有草稿可以修改，已发布的公告正文不再变动");
        }
        Range range = normalizeRange(entity.getInstitutionId(), request);
        applyDraftFields(entity, request, range);
        announcementMapper.updateById(entity);
        return toView(entity, readCountsOf(List.of(id)).getOrDefault(id, 0L), displayNames(List.of(entity)));
    }

    @Override
    @Transactional
    public AnnouncementView publish(Long id) {
        AuthenticatedUser user = AuthContext.requireUser();
        MsgAnnouncementEntity entity = requireOwnAnnouncement(id, user.orgId());
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "公告当前状态是" + statusLabel(entity.getStatus()) + "，只有草稿能发布");
        }
        List<String> audienceCodes = splitCodes(entity.getAudienceCodes());
        if (audienceCodes.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "发布前请先选择至少一档接收对象");
        }
        // 草稿期到发布之间字典条目可能被停用，发布时按字典再校验一次
        dictValueValidator.validate(DICT_TYPE_ANNOUNCEMENT, "公告类型", entity.getTypeCode());

        Range range = new Range(entity.getTypeCode(), audienceCodes, entity.getCampusScope(),
            splitIds(entity.getCampusIds()), splitIds(entity.getClassIds()));
        Map<Long, String> receivers = resolveReceivers(entity.getInstitutionId(), range);
        if (receivers.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "当前范围解析出 0 个接收人，请检查接收对象与校区/班级范围（教师需已绑定登录账号，学员需已绑定家长账号）");
        }

        LocalDateTime publishedAt = LocalDateTime.now();
        List<MsgAnnouncementReceiverEntity> rows = new ArrayList<>(receivers.size());
        receivers.forEach((userId, audienceCode) -> {
            MsgAnnouncementReceiverEntity row = new MsgAnnouncementReceiverEntity();
            row.setInstitutionId(entity.getInstitutionId());
            row.setAnnouncementId(entity.getId());
            row.setUserId(userId);
            row.setAudienceCode(audienceCode);
            rows.add(row);
        });
        rows.forEach(receiverMapper::insert);

        entity.setStatus(STATUS_PUBLISHED);
        entity.setPublishedAt(publishedAt);
        entity.setPublishedBy(user.userId());
        entity.setReceiverTotal(receivers.size());
        announcementMapper.updateById(entity);
        return toView(entity, 0L, displayNames(List.of(entity)));
    }

    @Override
    @Transactional
    public AnnouncementView recall(Long id, String reason) {
        AuthenticatedUser user = AuthContext.requireUser();
        MsgAnnouncementEntity entity = requireOwnAnnouncement(id, user.orgId());
        if (!STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已发布的公告可以撤回");
        }
        entity.setStatus(STATUS_RECALLED);
        entity.setRecalledAt(LocalDateTime.now());
        entity.setRecalledBy(user.userId());
        entity.setRecallReason(hasText(reason) ? reason.trim() : null);
        announcementMapper.updateById(entity);
        // 接收人行保留：分母是发布时冻结的范围快照，撤回只改变可见性，不清空留痕
        return toView(entity, readCountsOf(List.of(id)).getOrDefault(id, 0L), displayNames(List.of(entity)));
    }

    @Override
    @Transactional
    public void deleteDraft(Long id) {
        MsgAnnouncementEntity entity = requireOwnAnnouncement(id, AuthContext.requireUser().orgId());
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已发布的公告只能撤回，不能删除");
        }
        announcementMapper.deleteById(id);
    }

    @Override
    public List<AnnouncementTemplateView> listTemplates() {
        Long institutionId = institutionOf(AuthContext.requireUser());
        return templateMapper.selectList(new LambdaQueryWrapper<MsgAnnouncementTemplateEntity>()
                .eq(MsgAnnouncementTemplateEntity::getInstitutionId, institutionId)
                .orderByDesc(MsgAnnouncementTemplateEntity::getCreatedAt))
            .stream()
            .map(this::toTemplateView)
            .toList();
    }

    @Override
    @Transactional
    public AnnouncementTemplateView createTemplate(AnnouncementTemplateUpsertRequest request) {
        Long institutionId = institutionOf(AuthContext.requireUser());
        Range range = normalizeRange(institutionId, request);
        MsgAnnouncementTemplateEntity entity = new MsgAnnouncementTemplateEntity();
        entity.setInstitutionId(institutionId);
        applyTemplateFields(entity, request, range);
        templateMapper.insert(entity);
        return toTemplateView(entity);
    }

    @Override
    @Transactional
    public AnnouncementTemplateView updateTemplate(Long id, AnnouncementTemplateUpsertRequest request) {
        MsgAnnouncementTemplateEntity entity = requireOwnTemplate(id, institutionOf(AuthContext.requireUser()));
        Range range = normalizeRange(entity.getInstitutionId(), request);
        applyTemplateFields(entity, request, range);
        templateMapper.updateById(entity);
        return toTemplateView(entity);
    }

    @Override
    public void deleteTemplate(Long id) {
        requireOwnTemplate(id, institutionOf(AuthContext.requireUser()));
        templateMapper.deleteById(id);
    }

    @Override
    public PageResult<MyAnnouncementView> pageMine(int page, int pageSize, boolean unreadOnly) {
        AuthenticatedUser user = AuthContext.requireUser();
        Long institutionId = institutionOf(user);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Long total = receiverMapper.countMyAnnouncements(user.userId(), institutionId, unreadOnly);
        long nullTotal = nullToZero(total);
        if (nullTotal == 0) {
            return new PageResult<>(List.of(), nullTotal, safePage, safeSize);
        }
        List<Long> ids = receiverMapper.selectMyAnnouncementIds(user.userId(), institutionId, unreadOnly,
            (long) (safePage - 1) * safeSize, safeSize);
        if (ids.isEmpty()) {
            return new PageResult<>(List.of(), nullTotal, safePage, safeSize);
        }
        Map<Long, MsgAnnouncementEntity> announcements = announcementMapper.selectBatchIds(ids).stream()
            .collect(Collectors.toMap(MsgAnnouncementEntity::getId, Function.identity()));
        Map<Long, MsgAnnouncementReceiverEntity> relations = receiverMapper
            .selectList(new LambdaQueryWrapper<MsgAnnouncementReceiverEntity>()
                .eq(MsgAnnouncementReceiverEntity::getUserId, user.userId())
                .in(MsgAnnouncementReceiverEntity::getAnnouncementId, ids))
            .stream()
            .collect(Collectors.toMap(MsgAnnouncementReceiverEntity::getAnnouncementId, Function.identity(),
                (first, second) -> first));
        // 顺序按解析 SQL 的置顶+发布时间排，selectBatchIds 回来的顺序不可依赖
        List<MyAnnouncementView> views = new ArrayList<>(ids.size());
        for (Long id : ids) {
            MsgAnnouncementEntity entity = announcements.get(id);
            MsgAnnouncementReceiverEntity relation = relations.get(id);
            if (entity == null || relation == null) {
                continue;
            }
            views.add(toMineView(entity, relation));
        }
        return new PageResult<>(views, nullTotal, safePage, safeSize);
    }

    @Override
    public long countMyUnread() {
        AuthenticatedUser user = AuthContext.requireUser();
        return nullToZero(receiverMapper.countMyAnnouncements(user.userId(), institutionOf(user), true));
    }

    @Override
    @Transactional
    public MyAnnouncementView openMine(Long id) {
        AuthenticatedUser user = AuthContext.requireUser();
        Long institutionId = institutionOf(user);
        MsgAnnouncementEntity entity = announcementMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getInstitutionId(), institutionId)
            || !STATUS_PUBLISHED.equals(entity.getStatus())) {
            // 非接收人、草稿、已撤回一律同一个结论：这条公告对你不存在
            throw new BusinessException(ResultCode.NOT_FOUND, "公告不存在或已撤回");
        }
        MsgAnnouncementReceiverEntity relation = receiverMapper.selectOne(
            new LambdaQueryWrapper<MsgAnnouncementReceiverEntity>()
                .eq(MsgAnnouncementReceiverEntity::getAnnouncementId, id)
                .eq(MsgAnnouncementReceiverEntity::getUserId, user.userId()));
        if (relation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "公告不存在或已撤回");
        }
        if (relation.getReadAt() == null) {
            // 打开即已读：靠 read_at IS NULL 保证幂等，重复打开保持首次已读时间
            receiverMapper.markRead(id, user.userId(), LocalDateTime.now());
            relation.setReadAt(LocalDateTime.now());
        }
        return toMineView(entity, relation);
    }

    private MsgAnnouncementEntity requireOwnAnnouncement(Long id, Long institutionId) {
        MsgAnnouncementEntity entity = announcementMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "公告不存在");
        }
        if (!Objects.equals(entity.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他机构的公告");
        }
        return entity;
    }

    private Long institutionOf(AuthenticatedUser user) {
        Long institutionId = user.orgId();
        if (institutionId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前账号未归属任何机构，无法收发公告");
        }
        return institutionId;
    }

    private void applyDraftFields(MsgAnnouncementEntity entity, AnnouncementUpsertRequest request, Range range) {
        entity.setTitle(request.title().trim());
        entity.setContent(request.content());
        entity.setTypeCode(range.typeCode());
        entity.setTopFlag(Boolean.TRUE.equals(request.top()));
        entity.setAudienceCodes(String.join(",", range.audienceCodes()));
        entity.setCampusScope(range.campusScope());
        entity.setCampusIds(joinIds(range.campusIds()));
        entity.setClassIds(joinIds(range.classIds()));
        entity.setRemark(hasText(request.remark()) ? request.remark().trim() : null);
    }

    /** 模板的范围字段与公告草稿落同一套形状（逗号快照），套用时才能原样搬进表单。 */
    private void applyTemplateFields(MsgAnnouncementTemplateEntity entity,
                                     AnnouncementTemplateUpsertRequest request, Range range) {
        entity.setName(request.name().trim());
        entity.setTitle(request.title().trim());
        entity.setContent(request.content());
        entity.setTypeCode(range.typeCode());
        entity.setTopFlag(Boolean.TRUE.equals(request.top()));
        entity.setAudienceCodes(String.join(",", range.audienceCodes()));
        entity.setCampusScope(range.campusScope());
        entity.setCampusIds(joinIds(range.campusIds()));
        entity.setClassIds(joinIds(range.classIds()));
        entity.setRemark(hasText(request.remark()) ? request.remark().trim() : null);
    }

    private MsgAnnouncementTemplateEntity requireOwnTemplate(Long id, Long institutionId) {
        MsgAnnouncementTemplateEntity entity = templateMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "模板不存在");
        }
        if (!Objects.equals(entity.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他机构的模板");
        }
        return entity;
    }

    private AnnouncementTemplateView toTemplateView(MsgAnnouncementTemplateEntity entity) {
        return new AnnouncementTemplateView(
            entity.getId(),
            entity.getName(),
            entity.getTitle(),
            entity.getContent(),
            entity.getTypeCode(),
            Boolean.TRUE.equals(entity.getTopFlag()),
            splitCodes(entity.getAudienceCodes()),
            entity.getCampusScope(),
            splitIds(entity.getCampusIds()),
            splitIds(entity.getClassIds()),
            entity.getRemark());
    }

    private Range normalizeRange(Long institutionId, AnnouncementUpsertRequest request) {
        return normalizeRange(institutionId, request.typeCode(), request.audienceCodes(), request.campusScope(),
            request.campusIds(), request.classIds());
    }

    /** 模板与公告草稿共用同一套范围规则，差别只在模板的标题正文必填（在 DTO 上校验）。 */
    private Range normalizeRange(Long institutionId, AnnouncementTemplateUpsertRequest request) {
        return normalizeRange(institutionId, request.typeCode(), request.audienceCodes(), request.campusScope(),
            request.campusIds(), request.classIds());
    }

    /**
     * 把请求里的范围字段洗成可落库的快照：字典校验、接收对象取值校验、校区/班级归属校验、长度校验。
     * 草稿允许接收对象为空（还没想好发谁），但给了就必须是合法档位。
     */
    private Range normalizeRange(Long institutionId, String rawTypeCode, List<String> rawAudienceCodes,
                                 String rawCampusScope, List<Long> rawCampusIds, List<Long> rawClassIds) {
        String typeCode = dictValueValidator.validate(DICT_TYPE_ANNOUNCEMENT, "公告类型", rawTypeCode);
        List<String> audienceCodes = rawAudienceCodes == null ? List.of()
            : rawAudienceCodes.stream().filter(AnnouncementServiceImpl::hasText).map(String::trim).distinct().toList();
        if (!AUDIENCE_VALUES.containsAll(audienceCodes)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "接收对象取值无效，可选值：" + String.join("/", AUDIENCE_VALUES));
        }
        String campusScope = hasText(rawCampusScope) ? rawCampusScope.trim() : SCOPE_ALL;
        if (!SCOPE_ALL.equals(campusScope) && !SCOPE_PART.equals(campusScope)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "校区范围取值无效，可选值：all/part");
        }
        List<Long> campusIds = rawCampusIds == null ? List.of()
            : rawCampusIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Long> classIds = rawClassIds == null ? List.of()
            : rawClassIds.stream().filter(Objects::nonNull).distinct().toList();
        if (SCOPE_ALL.equals(campusScope)) {
            campusIds = List.of();
        } else if (campusIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "指定校区范围至少要选一个校区");
        }
        requireScopeOwnedByInstitution(campusIds, classIds, institutionId);
        return new Range(typeCode, audienceCodes, campusScope, campusIds, classIds);
    }

    /** 校区/班级只是筛选条件，必须逐一确认属于当前机构，不能拿别的机构的 ID 组范围。 */
    private void requireScopeOwnedByInstitution(List<Long> campusIds, List<Long> classIds, Long institutionId) {
        if (!campusIds.isEmpty()) {
            requireJoinedLength(campusIds, "校区");
            Long owned = campusMapper.selectCount(new LambdaQueryWrapper<CampusEntity>()
                .eq(CampusEntity::getInstitutionId, institutionId)
                .in(CampusEntity::getId, campusIds));
            if (nullToZero(owned) != campusIds.size()) {
                throw new BusinessException(ResultCode.FORBIDDEN, "所选校区不属于当前机构");
            }
        }
        if (!classIds.isEmpty()) {
            requireJoinedLength(classIds, "班级");
            Long owned = classMapper.selectCount(new LambdaQueryWrapper<TeachingClassEntity>()
                .eq(TeachingClassEntity::getInstitutionId, institutionId)
                .in(TeachingClassEntity::getId, classIds));
            if (nullToZero(owned) != classIds.size()) {
                throw new BusinessException(ResultCode.FORBIDDEN, "所选班级不属于当前机构");
            }
        }
    }

    private void requireJoinedLength(List<Long> ids, String label) {
        if (joinIds(ids).length() > SCOPE_COLUMN_MAX_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "所选" + label + "过多（超出单个公告的范围快照上限），请缩小范围或分批发布");
        }
    }

    /**
     * 按档位解析接收人并去重：同一账号命中多档只留第一次命中的档位（教师优先于家长优先于全部账号），
     * 因为接收人表的 (announcement_id, user_id) 是唯一键，一个人只能一行。
     */
    private Map<Long, String> resolveReceivers(Long institutionId, Range range) {
        Set<Long> seen = new LinkedHashSet<>();
        Map<Long, String> receivers = new LinkedHashMap<>();
        if (range.audienceCodes().contains(AUDIENCE_TEACHER)) {
            collect(receiverMapper.selectTeacherUserIds(institutionId, range.campusIds()), AUDIENCE_TEACHER, seen, receivers);
        }
        if (range.audienceCodes().contains(AUDIENCE_PARENT)) {
            collect(receiverMapper.selectParentUserIds(institutionId, range.campusIds(), range.classIds()),
                AUDIENCE_PARENT, seen, receivers);
        }
        if (range.audienceCodes().contains(AUDIENCE_ACCOUNT)) {
            collect(receiverMapper.selectAccountUserIds(institutionId), AUDIENCE_ACCOUNT, seen, receivers);
        }
        return receivers;
    }

    private void collect(List<Long> userIds, String audienceCode, Set<Long> seen, Map<Long, String> receivers) {
        if (userIds == null) {
            return;
        }
        for (Long userId : userIds) {
            if (userId != null && seen.add(userId)) {
                receivers.put(userId, audienceCode);
            }
        }
    }

    private List<String> buildNotices(Range range, Map<Long, String> receivers, int unboundStudentCount) {
        List<String> notices = new ArrayList<>();
        if (range.audienceCodes().isEmpty()) {
            notices.add("尚未选择接收对象，发布时才解析接收人");
        }
        if (SCOPE_PART.equals(range.campusScope())) {
            notices.add("已限定 " + range.campusIds().size() + " 个校区");
        }
        if (!range.classIds().isEmpty()) {
            notices.add("已限定 " + range.classIds().size() + " 个班级，家长档按这些班级的在读学员解析");
        }
        if (range.audienceCodes().contains(AUDIENCE_ACCOUNT)) {
            notices.add("「全部登录账号」一档不受校区范围限制，机构下启用账号都会收到");
        }
        if (unboundStudentCount > 0) {
            notices.add("另有 " + unboundStudentCount + " 名在读学员未绑定登录账号，其家长收不到这条公告");
        }
        if (receivers.isEmpty() && !range.audienceCodes().isEmpty()) {
            notices.add("当前范围内没有已绑定登录账号的接收人，发布会被拒绝");
        }
        return notices;
    }

    private Integer countByAudience(Map<Long, String> receivers, String audienceCode) {
        return (int) receivers.values().stream().filter(audienceCode::equals).count();
    }

    /** 每行的已读人数一次聚合取回，不在列表里逐条 count。 */
    private Map<Long, Long> readCountsOf(List<Long> announcementIds) {
        if (announcementIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = receiverMapper.selectMaps(new QueryWrapper<MsgAnnouncementReceiverEntity>()
            // 别名不带下划线：Map 结果的键直接取列标签，避免与 mapUnderscoreToCamelCase 配置相互影响
            .select("announcement_id AS announcementId", "COUNT(read_at) AS readCount")
            .in("announcement_id", announcementIds)
            .groupBy("announcement_id"));
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("announcementId");
            Object count = row.get("readCount");
            if (id instanceof Number idNumber && count instanceof Number countNumber) {
                counts.put(idNumber.longValue(), countNumber.longValue());
            }
        }
        return counts;
    }

    /** 发布人/撤回人的中文名：批量取一次，别在循环里查。 */
    private Map<Long, String> displayNames(Collection<MsgAnnouncementEntity> entities) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (MsgAnnouncementEntity entity : entities) {
            if (entity.getPublishedBy() != null) {
                userIds.add(entity.getPublishedBy());
            }
            if (entity.getRecalledBy() != null) {
                userIds.add(entity.getRecalledBy());
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userAccountMapper.selectBatchIds(userIds).stream()
            .collect(Collectors.toMap(UserAccountEntity::getId, this::nameOf, (first, second) -> first));
    }

    private String nameOf(UserAccountEntity account) {
        return hasText(account.getDisplayName()) ? account.getDisplayName() : account.getUsername();
    }

    private AnnouncementView toView(MsgAnnouncementEntity entity, Long readCount, Map<Long, String> names) {
        return new AnnouncementView(
            entity.getId(),
            entity.getTitle(),
            entity.getContent(),
            entity.getTypeCode(),
            entity.getStatus(),
            Boolean.TRUE.equals(entity.getTopFlag()),
            splitCodes(entity.getAudienceCodes()),
            entity.getCampusScope(),
            splitIds(entity.getCampusIds()),
            splitIds(entity.getClassIds()),
            entity.getReceiverTotal(),
            readCount == null ? 0 : readCount.intValue(),
            entity.getPublishedAt(),
            entity.getPublishedBy() == null ? null : names.get(entity.getPublishedBy()),
            entity.getRecalledAt(),
            entity.getRecalledBy() == null ? null : names.get(entity.getRecalledBy()),
            entity.getRecallReason(),
            entity.getRemark(),
            entity.getCreatedAt()
        );
    }

    private MyAnnouncementView toMineView(MsgAnnouncementEntity entity, MsgAnnouncementReceiverEntity relation) {
        return new MyAnnouncementView(
            entity.getId(),
            entity.getTitle(),
            entity.getContent(),
            entity.getTypeCode(),
            Boolean.TRUE.equals(entity.getTopFlag()),
            entity.getPublishedAt(),
            relation.getReadAt(),
            relation.getAudienceCode()
        );
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_DRAFT -> "草稿";
            case STATUS_PUBLISHED -> "已发布";
            case STATUS_RECALLED -> "已撤回";
            default -> status;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private static int intOf(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private static List<String> splitCodes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(AnnouncementServiceImpl::hasText).toList();
    }

    private static List<Long> splitIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(AnnouncementServiceImpl::hasText)
            .map(Long::valueOf)
            .toList();
    }

    private static String joinIds(List<Long> ids) {
        return ids == null || ids.isEmpty() ? null
            : ids.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 公告的范围快照：接收对象 + 校区/班级范围 + 已过字典校验的类型。 */
    private record Range(String typeCode, List<String> audienceCodes, String campusScope,
                         List<Long> campusIds, List<Long> classIds) {
    }
}
