package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.education.announcement.dto.AnnouncementRecipientPreview;
import com.cornerstone.module.education.announcement.dto.AnnouncementTemplateUpsertRequest;
import com.cornerstone.module.education.announcement.dto.AnnouncementTemplateView;
import com.cornerstone.module.education.announcement.dto.AnnouncementUpsertRequest;
import com.cornerstone.module.education.announcement.dto.AnnouncementView;
import com.cornerstone.module.education.announcement.dto.MyAnnouncementView;
import com.cornerstone.module.education.announcement.service.impl.AnnouncementServiceImpl;
import com.cornerstone.module.education.domain.entity.MsgAnnouncementEntity;
import com.cornerstone.module.education.domain.entity.MsgAnnouncementReceiverEntity;
import com.cornerstone.module.education.domain.entity.MsgAnnouncementTemplateEntity;
import com.cornerstone.module.education.mapper.MsgAnnouncementMapper;
import com.cornerstone.module.education.mapper.MsgAnnouncementReceiverMapper;
import com.cornerstone.module.education.mapper.MsgAnnouncementTemplateMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.support.DictValueValidator;
import com.cornerstone.module.organization.mapper.CampusMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 公告服务的范围解析、状态流转与租户边界。
 *
 * <p>接收人解析 SQL 本身（四段 @Select）由 Mockito 覆盖不到，那些在 V65 的 scratch 回放
 * 与真机冒烟里验；这里验的是解析结果之上的去重、拒绝发布与可见性判定。</p>
 */
class AnnouncementServiceImplTest {

    private final MsgAnnouncementMapper announcementMapper = mock(MsgAnnouncementMapper.class);
    private final MsgAnnouncementReceiverMapper receiverMapper = mock(MsgAnnouncementReceiverMapper.class);
    private final MsgAnnouncementTemplateMapper templateMapper = mock(MsgAnnouncementTemplateMapper.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final CampusMapper campusMapper = mock(CampusMapper.class);
    private final TeachingClassMapper classMapper = mock(TeachingClassMapper.class);
    private final SystemDictService systemDictService = mock(SystemDictService.class);

    private AnnouncementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementServiceImpl(announcementMapper, receiverMapper, templateMapper,
            userAccountMapper, campusMapper, classMapper, new DictValueValidator(systemDictService));
        when(systemDictService.listItems("announcement_type")).thenReturn(List.of(
            new DictItemView(72L, 14L, "holiday", "放假安排", "holiday", 10, "active", null),
            new DictItemView(73L, 14L, "schedule_change", "教务调整", "schedule_change", 20, "active", null),
            new DictItemView(74L, 14L, "teaching_notice", "教学通知", "teaching_notice", 30, "active", null),
            new DictItemView(75L, 14L, "urgent", "紧急", "urgent", 40, "active", null)));
        authenticate(1L, 1L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void previewDeduplicatesAccountsHitBySeveralAudiences() {
        // 200 既是教师又是某学员的家长：总数只算一次，档位记先命中的 teacher
        when(receiverMapper.selectTeacherUserIds(1L, List.of())).thenReturn(List.of(100L, 200L));
        when(receiverMapper.selectParentUserIds(1L, List.of(), List.of())).thenReturn(List.of(200L, 300L));
        when(receiverMapper.countUnboundStudents(1L, List.of(), List.of())).thenReturn(1L);

        AnnouncementRecipientPreview preview = service.previewRecipients(
            request(List.of("teacher", "parent"), "all", List.of(), List.of()));

        assertEquals(2, preview.teacherCount());
        assertEquals(1, preview.parentCount());
        assertEquals(0, preview.accountCount());
        assertEquals(3, preview.total());
        assertEquals(1, preview.unboundStudentCount());
        assertTrue(preview.notices().stream().anyMatch(item -> item.contains("未绑定登录账号")));
    }

    @Test
    void accountAudienceIsNotNarrowedByCampus() {
        when(campusMapper.selectCount(any())).thenReturn(1L);
        when(receiverMapper.selectAccountUserIds(1L)).thenReturn(List.of(7L, 8L));

        AnnouncementRecipientPreview preview = service.previewRecipients(
            request(List.of("account"), "part", List.of(11L), List.of()));

        assertEquals(2, preview.total());
        assertTrue(preview.notices().stream().anyMatch(item -> item.contains("不受校区范围限制")));
        verify(receiverMapper, never()).selectTeacherUserIds(any(), any());
    }

    @Test
    void publishRejectsEmptyRange() {
        when(announcementMapper.selectById(5L)).thenReturn(draft(5L, "teacher", "all", null, null));
        when(receiverMapper.selectTeacherUserIds(1L, List.of())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.publish(5L));

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        assertTrue(exception.getMessage().contains("0 个接收人"));
        verify(receiverMapper, never()).insert(any(MsgAnnouncementReceiverEntity.class));
    }

    @Test
    void publishExpandsReceiversAndFreezesDenominator() {
        MsgAnnouncementEntity entity = draft(5L, "teacher,parent", "all", null, null);
        when(announcementMapper.selectById(5L)).thenReturn(entity);
        when(receiverMapper.selectTeacherUserIds(1L, List.of())).thenReturn(List.of(100L, 200L));
        when(receiverMapper.selectParentUserIds(1L, List.of(), List.of())).thenReturn(List.of(300L));
        when(userAccountMapper.selectBatchIds(any())).thenReturn(List.of(account(1L, "校长")));

        AnnouncementView view = service.publish(5L);

        ArgumentCaptor<MsgAnnouncementReceiverEntity> captor =
            ArgumentCaptor.forClass(MsgAnnouncementReceiverEntity.class);
        verify(receiverMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        assertEquals(List.of(100L, 200L, 300L),
            captor.getAllValues().stream().map(MsgAnnouncementReceiverEntity::getUserId).toList());
        assertEquals("teacher", captor.getAllValues().get(0).getAudienceCode());
        assertEquals("parent", captor.getAllValues().get(2).getAudienceCode());
        assertEquals("published", view.status());
        assertEquals(3, view.receiverTotal());
        assertEquals("校长", view.publishedByName());
    }

    @Test
    void publishedAnnouncementIsNotEditable() {
        MsgAnnouncementEntity entity = draft(5L, "teacher", "all", null, null);
        entity.setStatus("published");
        when(announcementMapper.selectById(5L)).thenReturn(entity);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.updateDraft(5L, request(List.of("teacher"), "all", List.of(), List.of())));

        assertTrue(exception.getMessage().contains("只有草稿可以修改"));
    }

    @Test
    void onlyPublishedAnnouncementGetsWxacode() {
        when(announcementMapper.selectById(5L)).thenReturn(draft(5L, "teacher", "all", null, null));
        BusinessException draft = assertThrows(BusinessException.class, () -> service.requireShareable(5L));
        assertTrue(draft.getMessage().contains("已发布"));

        MsgAnnouncementEntity recalled = published(5L);
        recalled.setStatus("recalled");
        when(announcementMapper.selectById(5L)).thenReturn(recalled);
        assertThrows(BusinessException.class, () -> service.requireShareable(5L));

        when(announcementMapper.selectById(5L)).thenReturn(published(5L));
        assertDoesNotThrow(() -> service.requireShareable(5L));
    }

    @Test
    void recalledAnnouncementIsInvisibleToReceiver() {
        MsgAnnouncementEntity entity = draft(5L, "teacher", "all", null, null);
        entity.setStatus("recalled");
        when(announcementMapper.selectById(5L)).thenReturn(entity);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.openMine(5L));

        assertEquals(ResultCode.NOT_FOUND, exception.getResultCode());
        verify(receiverMapper, never()).markRead(any(), any(), any());
    }

    @Test
    void nonReceiverCannotOpenAnnouncement() {
        when(announcementMapper.selectById(5L)).thenReturn(published(5L));
        when(receiverMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.openMine(5L));
        verify(receiverMapper, never()).markRead(any(), any(), any());
    }

    @Test
    void openingDetailStampsReadOnlyOnce() {
        when(announcementMapper.selectById(5L)).thenReturn(published(5L));
        MsgAnnouncementReceiverEntity relation = receiver(5L, 1L, "teacher", null);
        when(receiverMapper.selectOne(any())).thenReturn(relation);

        MyAnnouncementView first = service.openMine(5L);

        verify(receiverMapper).markRead(org.mockito.Mockito.eq(5L), org.mockito.Mockito.eq(1L), any());
        assertTrue(first.readAt() != null);

        SecurityContextHolder.clearContext();
        authenticate(1L, 1L);
        when(receiverMapper.selectOne(any())).thenReturn(receiver(5L, 1L, "teacher", java.time.LocalDateTime.now()));

        service.openMine(5L);

        verify(receiverMapper, org.mockito.Mockito.times(1)).markRead(any(), any(), any());
    }

    @Test
    void otherInstitutionAnnouncementIsForbidden() {
        MsgAnnouncementEntity entity = published(5L);
        entity.setInstitutionId(2L);
        when(announcementMapper.selectById(5L)).thenReturn(entity);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.getAnnouncement(5L));

        assertEquals(ResultCode.FORBIDDEN, exception.getResultCode());
    }

    @Test
    void unknownTypeCodeIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.createDraft(request(List.of("teacher"), "all", List.of(), List.of(), "party")));

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        assertTrue(exception.getMessage().contains("可选值"));
        verify(announcementMapper, never()).insert(any(MsgAnnouncementEntity.class));
    }

    @Test
    void campusOutsideInstitutionIsRejected() {
        when(campusMapper.selectCount(any())).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.createDraft(request(List.of("teacher"), "part", List.of(99L), List.of())));

        assertEquals(ResultCode.FORBIDDEN, exception.getResultCode());
        assertTrue(exception.getMessage().contains("不属于当前机构"));
    }

    @Test
    void draftMayOmitAudience() {
        when(announcementMapper.selectById(5L)).thenReturn(draft(5L, null, "all", null, null));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.publish(5L));

        assertTrue(exception.getMessage().contains("至少一档接收对象"));
    }

    @Test
    void myPageKeepsResolvedOrderNotBatchIdsOrder() {
        // 解析 SQL 按置顶+发布时间排好了 3→1，selectBatchIds 回来的顺序不可依赖
        when(receiverMapper.countMyAnnouncements(1L, 1L, false)).thenReturn(2L);
        when(receiverMapper.selectMyAnnouncementIds(1L, 1L, false, 0L, 10)).thenReturn(List.of(3L, 1L));
        when(announcementMapper.selectBatchIds(List.of(3L, 1L)))
            .thenReturn(List.of(published(1L), published(3L)));
        when(receiverMapper.selectList(any())).thenReturn(List.of(
            receiver(1L, 1L, "teacher", null), receiver(3L, 1L, "parent", null)));

        PageResult<MyAnnouncementView> page = service.pageMine(1, 10, false);

        assertEquals(2L, page.total());
        assertEquals(List.of(3L, 1L), page.records().stream().map(MyAnnouncementView::id).toList());
        assertEquals("parent", page.records().get(0).audienceCode());
    }

    @Test
    void receiverPageAggregateIsTakenOncePerPage() {
        MsgAnnouncementEntity first = published(1L);
        MsgAnnouncementEntity second = published(2L);
        when(announcementMapper.selectPage(any(), any())).thenReturn(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<MsgAnnouncementEntity>()
                .setRecords(List.of(first, second)));
        when(receiverMapper.selectMaps(any())).thenReturn(List.of(
            java.util.Map.<String, Object>of("announcementId", 1L, "readCount", 4L)));
        when(userAccountMapper.selectBatchIds(any())).thenReturn(List.of(account(1L, "校长")));

        PageResult<AnnouncementView> page = service.pageAnnouncements(new com.cornerstone.module.education.announcement.dto.AnnouncementPageQuery());

        verify(receiverMapper, org.mockito.Mockito.times(1)).selectMaps(any());
        assertEquals(4, page.records().get(0).readCount());
        assertEquals(0, page.records().get(1).readCount());
        assertEquals("校长", page.records().get(0).publishedByName());
    }

    @Test
    void templateRejectsCampusOfAnotherInstitution() {
        when(campusMapper.selectCount(any())).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.createTemplate(templateRequest("part", List.of(99L), List.of())));

        assertEquals(ResultCode.FORBIDDEN, exception.getResultCode());
        assertTrue(exception.getMessage().contains("不属于当前机构"));
        verify(templateMapper, never()).insert(any(MsgAnnouncementTemplateEntity.class));
    }

    @Test
    void templateFreezesScopeAsCsvAndReadsItBackAsLists() {
        when(campusMapper.selectCount(any())).thenReturn(2L);

        AnnouncementTemplateView view = service.createTemplate(
            templateRequest("part", List.of(11L, 12L), List.of()));

        ArgumentCaptor<MsgAnnouncementTemplateEntity> captor =
            ArgumentCaptor.forClass(MsgAnnouncementTemplateEntity.class);
        verify(templateMapper).insert(captor.capture());
        // 落库存逗号快照（与公告同一形状），出参再拆回列表给套用人
        assertEquals("11,12", captor.getValue().getCampusIds());
        assertEquals(List.of(11L, 12L), view.campusIds());
        assertEquals(List.of(), view.classIds());
        assertEquals(1L, captor.getValue().getInstitutionId());
    }

    @Test
    void otherInstitutionTemplateIsForbidden() {
        MsgAnnouncementTemplateEntity entity = template(9L);
        entity.setInstitutionId(2L);
        when(templateMapper.selectById(9L)).thenReturn(entity);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.deleteTemplate(9L));

        assertEquals(ResultCode.FORBIDDEN, exception.getResultCode());
        verify(templateMapper, never()).deleteById(9L);
    }

    @Test
    void unknownTemplateIsNotFoundNotForbidden() {
        when(templateMapper.selectById(9L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.updateTemplate(9L, templateRequest("all", List.of(), List.of())));

        assertEquals(ResultCode.NOT_FOUND, exception.getResultCode());
    }

    private AnnouncementTemplateUpsertRequest templateRequest(String campusScope, List<Long> campusIds,
                                                              List<Long> classIds) {
        return new AnnouncementTemplateUpsertRequest("放假模板", "国庆放假通知", "10 月 1 日至 7 日放假。",
            "holiday", List.of("teacher"), campusScope, campusIds, classIds, false, null);
    }

    private MsgAnnouncementTemplateEntity template(Long id) {
        MsgAnnouncementTemplateEntity entity = new MsgAnnouncementTemplateEntity();
        entity.setId(id);
        entity.setInstitutionId(1L);
        entity.setName("放假模板");
        entity.setTitle("国庆放假通知");
        entity.setContent("10 月 1 日至 7 日放假。");
        entity.setTypeCode("holiday");
        entity.setTopFlag(false);
        entity.setAudienceCodes("teacher");
        entity.setCampusScope("all");
        return entity;
    }

    private AnnouncementUpsertRequest request(List<String> audienceCodes, String campusScope,
                                               List<Long> campusIds, List<Long> classIds) {
        return request(audienceCodes, campusScope, campusIds, classIds, "holiday");
    }

    private AnnouncementUpsertRequest request(List<String> audienceCodes, String campusScope,
                                               List<Long> campusIds, List<Long> classIds, String typeCode) {
        return new AnnouncementUpsertRequest("国庆放假通知", "10 月 1 日至 7 日放假。", typeCode,
            audienceCodes, campusScope, campusIds, classIds, false, null);
    }

    private MsgAnnouncementEntity draft(Long id, String audienceCodes, String campusScope,
                                        String campusIds, String classIds) {
        MsgAnnouncementEntity entity = new MsgAnnouncementEntity();
        entity.setId(id);
        entity.setInstitutionId(1L);
        entity.setTitle("国庆放假通知");
        entity.setContent("10 月 1 日至 7 日放假。");
        entity.setTypeCode("holiday");
        entity.setStatus("draft");
        entity.setTopFlag(false);
        entity.setAudienceCodes(audienceCodes);
        entity.setCampusScope(campusScope);
        entity.setCampusIds(campusIds);
        entity.setClassIds(classIds);
        entity.setReceiverTotal(0);
        return entity;
    }

    private MsgAnnouncementEntity published(Long id) {
        MsgAnnouncementEntity entity = draft(id, "teacher", "all", null, null);
        entity.setStatus("published");
        entity.setPublishedAt(java.time.LocalDateTime.now());
        entity.setPublishedBy(1L);
        entity.setReceiverTotal(8);
        return entity;
    }

    private MsgAnnouncementReceiverEntity receiver(Long announcementId, Long userId, String audienceCode,
                                                    java.time.LocalDateTime readAt) {
        MsgAnnouncementReceiverEntity entity = new MsgAnnouncementReceiverEntity();
        entity.setAnnouncementId(announcementId);
        entity.setUserId(userId);
        entity.setAudienceCode(audienceCode);
        entity.setReadAt(readAt);
        return entity;
    }

    private UserAccountEntity account(Long id, String displayName) {
        UserAccountEntity account = new UserAccountEntity();
        account.setId(id);
        account.setUsername("admin");
        account.setDisplayName(displayName);
        return account;
    }

    @Test
    void restrictedUserCannotOpenAnnouncementOfAnotherCampus() {
        authenticateScoped(Set.of(11L));
        when(announcementMapper.selectById(5L)).thenReturn(draft(5L, "teacher", "part", "12", null));

        BusinessException error = assertThrows(BusinessException.class, () -> service.getAnnouncement(5L));

        assertEquals(ResultCode.FORBIDDEN, error.getResultCode());
    }

    @Test
    void restrictedUserCanOpenIntersectingPartAndAllScopeAnnouncements() {
        authenticateScoped(Set.of(11L));
        when(announcementMapper.selectById(5L)).thenReturn(draft(5L, "teacher", "part", "10,11", null));
        when(announcementMapper.selectById(6L)).thenReturn(draft(6L, "teacher", "all", null, null));
        when(receiverMapper.selectMaps(any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getAnnouncement(5L));
        assertDoesNotThrow(() -> service.getAnnouncement(6L));
    }

    @Test
    void emptyCampusWhitelistHidesEvenAllScopeAnnouncements() {
        authenticateScoped(Set.of());
        when(announcementMapper.selectById(5L)).thenReturn(draft(5L, "teacher", "all", null, null));

        assertThrows(BusinessException.class, () -> service.getAnnouncement(5L));
    }

    private void authenticateScoped(Set<Long> campusIds) {
        AuthenticatedUser user = new AuthenticatedUser(1L, 1L, "admin", Set.of("ADMIN"),
            Set.of("announcement:read", "announcement:write"), false, "admin", "CAMPUS", campusIds);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }

    private void authenticate(Long userId, Long institutionId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, institutionId, "admin",
            Set.of("ADMIN"), Set.of("announcement:read", "announcement:write"), false, "admin");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }
}
