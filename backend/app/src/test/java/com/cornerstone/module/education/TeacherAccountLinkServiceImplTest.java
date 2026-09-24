package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.dto.TeacherAccountLinkCandidateResult;
import com.cornerstone.module.education.dto.TeacherAccountLinkCandidateView;
import com.cornerstone.module.education.dto.TeacherAccountLinkRequest;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.service.impl.TeacherAccountLinkServiceImpl;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 教师档案与登录账号的按手机号匹配。
 *
 * <p>重点验两件事：候选行能不能解释清楚"为什么没匹配上"，以及绑定前的归属与占用校验
 * 会不会漏放一条能写出跨机构关联的请求。</p>
 */
class TeacherAccountLinkServiceImplTest {

    private static final long INSTITUTION_ID = 1L;
    private static final long OTHER_INSTITUTION_ID = 2L;
    private static final String PHONE = "13800000001";

    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);

    private TeacherAccountLinkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TeacherAccountLinkServiceImpl(teacherMapper, userAccountMapper);
        authenticate();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 候选

    @Test
    void matchedTeacherBecomesActionableCandidateWithNameFlag() {
        givenUnlinkedTeachers(List.of(teacher(10L, "T001", "张老师", PHONE)));
        givenAccounts(List.of(account(70L, INSTITUTION_ID, "zhang", "张老师", PHONE, "active")));

        TeacherAccountLinkCandidateView view = service.previewCandidates().rows().get(0);

        assertTrue(view.actionable());
        assertEquals(70L, view.userId().longValue());
        assertEquals(Boolean.TRUE, view.nameMatched());
    }

    @Test
    void nameMismatchStillActionableButFlagged() {
        // 姓名不一致只影响默认勾选，不该由服务端替人决定绑不绑
        givenUnlinkedTeachers(List.of(teacher(10L, "T001", "张老师", PHONE)));
        givenAccounts(List.of(account(70L, INSTITUTION_ID, "zhang", "张某", PHONE, "active")));

        TeacherAccountLinkCandidateView view = service.previewCandidates().rows().get(0);

        assertEquals(Boolean.FALSE, view.nameMatched());
        assertTrue(view.actionable());
    }

    @Test
    void teacherWithoutPhoneExplainsWhyItCannotMatch() {
        givenUnlinkedTeachers(List.of(teacher(10L, "T001", "张老师", null)));

        TeacherAccountLinkCandidateResult result = service.previewCandidates();

        assertEquals(1, result.unlinkedTotal().intValue());
        assertEquals(0, result.matchableTotal().intValue());
        assertTrue(result.rows().get(0).notice().contains("未填手机号"));
        verify(userAccountMapper, never()).selectList(any());
    }

    @Test
    void teacherWithoutAccountIsToldWhereToOpenOne() {
        givenUnlinkedTeachers(List.of(teacher(10L, "T001", "张老师", PHONE)));
        when(userAccountMapper.selectList(any())).thenReturn(List.of());

        TeacherAccountLinkCandidateView view = service.previewCandidates().rows().get(0);

        assertFalse(view.actionable());
        assertTrue(view.notice().contains("批量导入账号"), view.notice());
    }

    @Test
    void multipleAccountsOnOnePhoneSplitIntoOneRowEach() {
        givenUnlinkedTeachers(List.of(teacher(10L, "T001", "张老师", PHONE)));
        givenAccounts(List.of(
            account(70L, INSTITUTION_ID, "zhang1", "张老师", PHONE, "active"),
            account(71L, INSTITUTION_ID, "zhang2", "老张", PHONE, "active")));

        TeacherAccountLinkCandidateResult result = service.previewCandidates();

        assertEquals(2, result.candidateCount().intValue());
        assertTrue(result.rows().stream().allMatch(row -> row.notice().contains("人工确认")));
        assertEquals(List.of(70L, 71L), result.rows().stream().map(TeacherAccountLinkCandidateView::userId).toList());
    }

    @Test
    void accountAlreadyHeldByAnotherTeacherIsNotActionable() {
        givenUnlinkedTeachers(List.of(teacher(10L, "T001", "张老师", PHONE)));
        givenAccounts(List.of(account(70L, INSTITUTION_ID, "zhang", "张老师", PHONE, "active")));
        // 占用方来自别家机构：uk_jp_teacher_user_id 是全表唯一键，不带机构条件才看得见
        when(teacherMapper.selectList(any())).thenReturn(
            List.of(teacher(10L, "T001", "张老师", PHONE)),
            List.of(teacherOnOtherInstitution(99L, OTHER_INSTITUTION_ID, 70L)));

        TeacherAccountLinkCandidateView view = service.previewCandidates().rows().get(0);

        assertFalse(view.actionable());
        assertTrue(view.notice().contains("已绑定其他教师档案"), view.notice());
    }

    // ------------------------------------------------------------------ 绑定

    @Test
    void linkWritesThePairAndReportsTheCount() {
        givenTeacher(teacher(10L, "T001", "张老师", PHONE));
        givenAccountFound(account(70L, INSTITUTION_ID, "zhang", "张老师", PHONE, "active"));
        when(teacherMapper.update(any(), any())).thenReturn(1);

        assertEquals(1, service.link(request(new TeacherAccountLinkRequest.Link(10L, 70L))).linkedCount().intValue());
    }

    @Test
    void linkRejectsTeacherOfAnotherInstitution() {
        givenTeacher(teacherOnOtherInstitution(10L, OTHER_INSTITUTION_ID, null));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.link(request(new TeacherAccountLinkRequest.Link(10L, 70L))));

        assertTrue(exception.getMessage().contains("不属于当前机构"), exception.getMessage());
        verify(teacherMapper, never()).update(any(), any());
    }

    @Test
    void linkRejectsTeacherThatAlreadyHasAnAccount() {
        TeacherEntity bound = teacher(10L, "T001", "张老师", PHONE);
        bound.setUserId(70L);
        givenTeacher(bound);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.link(request(new TeacherAccountLinkRequest.Link(10L, 70L))));

        assertTrue(exception.getMessage().contains("已绑定账号"), exception.getMessage());
    }

    @Test
    void linkRejectsAccountOfAnotherInstitution() {
        givenTeacher(teacher(10L, "T001", "张老师", PHONE));
        givenAccountFound(account(70L, OTHER_INSTITUTION_ID, "zhang", "张老师", PHONE, "active"));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.link(request(new TeacherAccountLinkRequest.Link(10L, 70L))));

        assertTrue(exception.getMessage().contains("不属于当前机构"), exception.getMessage());
    }

    @Test
    void linkRejectsAccountAlreadyHeldByAnotherTeacher() {
        givenTeacher(teacher(10L, "T001", "张老师", PHONE));
        givenAccountFound(account(70L, INSTITUTION_ID, "zhang", "张老师", PHONE, "active"));
        when(teacherMapper.selectOne(any())).thenReturn(teacherOnOtherInstitution(99L, INSTITUTION_ID, 70L));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.link(request(new TeacherAccountLinkRequest.Link(10L, 70L))));

        assertTrue(exception.getMessage().contains("已绑定其他教师档案"), exception.getMessage());
        verify(teacherMapper, never()).update(any(), any());
    }

    @Test
    void linkRejectsTheSameTeacherTwiceInOneSubmit() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.link(request(new TeacherAccountLinkRequest.Link(10L, 70L),
                new TeacherAccountLinkRequest.Link(10L, 71L))));

        assertTrue(exception.getMessage().contains("出现多次"), exception.getMessage());
    }

    /** 前置校验通过但落库时那行已被别人绑走：带 user_id IS NULL 的更新返回 0 行，必须报错而不是静默覆盖 */
    @Test
    void zeroRowUpdateRollsBackWithActionableMessage() {
        givenTeacher(teacher(10L, "T001", "张老师", PHONE));
        givenAccountFound(account(70L, INSTITUTION_ID, "zhang", "张老师", PHONE, "active"));
        when(teacherMapper.update(any(), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.link(request(new TeacherAccountLinkRequest.Link(10L, 70L))));

        assertTrue(exception.getMessage().contains("已被其他操作改变"), exception.getMessage());
    }

    @Test
    void accountWithoutInstitutionCannotLink() {
        authenticate(null);

        BusinessException exception = assertThrows(BusinessException.class, service::previewCandidates);

        assertTrue(exception.getMessage().contains("未绑定机构"), exception.getMessage());
    }

    // ------------------------------------------------------------------ 造数据

    private TeacherAccountLinkRequest request(TeacherAccountLinkRequest.Link... links) {
        return new TeacherAccountLinkRequest(List.of(links));
    }

    /**
     * 档案候选页取两次 selectList：第一次是未绑定账号的档案，第二次是「候选账号已被谁占用」。
     * 想覆盖占用场景时改用两值 thenReturn。
     */
    private void givenUnlinkedTeachers(List<TeacherEntity> unlinked) {
        when(teacherMapper.selectList(any())).thenReturn(unlinked, List.of());
    }

    private void givenAccounts(List<UserAccountEntity> accounts) {
        when(userAccountMapper.selectList(any())).thenReturn(accounts);
    }

    private void givenTeacher(TeacherEntity teacher) {
        when(teacherMapper.selectById(teacher.getId())).thenReturn(teacher);
    }

    private void givenAccountFound(UserAccountEntity account) {
        when(userAccountMapper.selectById(account.getId())).thenReturn(account);
    }

    private TeacherEntity teacher(Long id, String teacherCode, String teacherName, String phone) {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(id);
        teacher.setInstitutionId(INSTITUTION_ID);
        teacher.setTeacherCode(teacherCode);
        teacher.setTeacherName(teacherName);
        teacher.setPhone(phone);
        return teacher;
    }

    private TeacherEntity teacherOnOtherInstitution(Long id, Long institutionId, Long userId) {
        TeacherEntity teacher = teacher(id, "T" + id, "别家教师", null);
        teacher.setInstitutionId(institutionId);
        teacher.setUserId(userId);
        return teacher;
    }

    private UserAccountEntity account(Long id, Long institutionId, String username, String displayName,
                                      String phone, String status) {
        UserAccountEntity account = new UserAccountEntity();
        account.setId(id);
        account.setInstitutionId(institutionId);
        account.setUsername(username);
        account.setDisplayName(displayName);
        account.setPhone(phone);
        account.setStatus(status);
        return account;
    }

    private void authenticate() {
        authenticate(INSTITUTION_ID);
    }

    private void authenticate(Long orgId) {
        AuthenticatedUser user = new AuthenticatedUser(1L, orgId, "admin",
            Set.of("EDU"), Set.of("education:write"), false, null);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", List.of()));
    }
}
