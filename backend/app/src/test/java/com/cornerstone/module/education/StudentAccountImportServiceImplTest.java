package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
import com.cornerstone.core.auth.dto.InvitationView;
import com.cornerstone.core.auth.dto.UserCreateRequest;
import com.cornerstone.core.auth.dto.UserView;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.service.AuthInvitationService;
import com.cornerstone.core.auth.service.SystemUserService;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.UserStudentRelEntity;
import com.cornerstone.module.education.dto.StudentAccountActivationRequest;
import com.cornerstone.module.education.dto.StudentAccountActivationView;
import com.cornerstone.module.education.dto.StudentAccountImportResult;
import com.cornerstone.module.education.dto.StudentAccountImportRowView;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.UserStudentRelMapper;
import com.cornerstone.module.education.service.impl.MiniappPortalResolver;
import com.cornerstone.module.education.service.impl.StudentAccountImportServiceImpl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 账号批量导入：用真实 xlsx 走一遍解析、校验、落库与批量发码。
 *
 * <p>刻意用 POI 造文件而不是直接喂 DTO——列顺序、数字单元格、表头行这三处
 * 是客户手填最容易坏的地方，只有在真文件上才验得出来。</p>
 */
class StudentAccountImportServiceImplTest {

    private static final long INSTITUTION_ID = 1L;
    private static final String PHONE_MUM = "13800000001";
    private static final String PHONE_DAD = "13800000002";

    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final UserStudentRelMapper userStudentRelMapper = mock(UserStudentRelMapper.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final UserRoleRelMapper userRoleRelMapper = mock(UserRoleRelMapper.class);
    private final SystemUserService systemUserService = mock(SystemUserService.class);
    private final AuthInvitationService authInvitationService = mock(AuthInvitationService.class);

    private final AtomicLong nextUserId = new AtomicLong(100L);

    private StudentAccountImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentAccountImportServiceImpl(studentMapper, userStudentRelMapper,
            userAccountMapper, roleMapper, userRoleRelMapper, systemUserService, authInvitationService);
        authenticate();
        when(studentMapper.selectList(any())).thenReturn(List.of(
            student(10L, "S001", "小明", null),
            student(11L, "S002", "小红", null),
            student(12L, "S003", "小刚", 99L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(
            role(20L, MiniappPortalResolver.ROLE_CODE_PARENT),
            role(21L, MiniappPortalResolver.ROLE_CODE_STUDENT)));
        when(userAccountMapper.selectList(any())).thenReturn(List.of());
        when(userRoleRelMapper.selectCount(any())).thenReturn(0L);
        when(userStudentRelMapper.selectList(any())).thenReturn(List.of());
        when(systemUserService.createUser(any())).thenAnswer(invocation ->
            userView(nextUserId.getAndIncrement(), invocation.getArgument(0)));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 校验

    @Test
    void oneAccountCanBindMultipleStudents() {
        // 同一个手机号写两行 = 一个账号绑两个孩子，不该被误判成重复或冲突
        StudentAccountImportResult result = service.preview(workbook(List.of(
            row("S001", "家长", "妈妈", PHONE_MUM, null),
            row("S002", "家长", "妈妈", PHONE_MUM, null)), null), INSTITUTION_ID);

        assertEquals(2, result.total().intValue());
        assertEquals(0, result.errorCount().intValue());
        assertEquals(2, result.validCount().intValue());
    }

    @Test
    void numericPhoneCellIsRestoredAndBecomesDefaultUsername() {
        // Excel 把手机号存成数值时 EasyExcel 会给到科学计数法形态，必须还原成 11 位整串
        StudentAccountImportResult result = service.preview(workbook(List.of(
            row("S001", "家长", "妈妈", null, null)), PHONE_MUM), INSTITUTION_ID);

        StudentAccountImportRowView view = result.rows().get(0);
        assertEquals(PHONE_MUM, view.phone());
        assertEquals(PHONE_MUM, view.username());
        assertEquals(0, result.errorCount().intValue());
    }

    @Test
    void studentWithoutPhoneLogsInWithStudentNo() {
        // 现场很多学生没有手机，登录账号要能自动落到学号上，而不是逼业务员编一个假号
        StudentAccountImportResult result = service.preview(workbook(List.of(
            row("S001", "学生本人", "小明", null, null)), null), INSTITUTION_ID);

        StudentAccountImportRowView view = result.rows().get(0);
        assertNull(view.phone(), view.error());
        assertEquals("S001", view.username());
        assertEquals(0, result.errorCount().intValue(), view.error());
    }

    @Test
    void sameUsernameHeldByAnotherInstitutionIsRejectedNotReused() {
        // 登录账号是全局唯一键：别家机构有同名待激活账号时不能当"自家账号已存在"复用，
        // 否则本机构学员会绑到别人家的账号上
        UserAccountEntity foreign = account(77L, PHONE_MUM, AuthInvitationService.STATUS_PENDING_ACTIVATION);
        foreign.setInstitutionId(999L);
        when(userAccountMapper.selectList(any())).thenReturn(List.of(foreign));

        StudentAccountImportResult result = service.preview(workbook(List.of(
            row("S001", "家长", "妈妈", PHONE_MUM, null)), null), INSTITUTION_ID);

        assertEquals(1, result.errorCount().intValue());
        assertTrue(result.rows().get(0).error().contains("其他机构"), result.rows().get(0).error());
    }

    @Test
    void unknownStudentAndBadPhoneAreReportedPerRow() {
        StudentAccountImportResult result = service.preview(workbook(List.of(
            row("S999", "家长", "妈妈", "123", null)), null), INSTITUTION_ID);

        assertEquals(1, result.errorCount().intValue());
        StudentAccountImportRowView view = result.rows().get(0);
        assertFalse(view.valid());
        assertTrue(view.error().contains("未找到学员编号"), view.error());
        assertTrue(view.error().contains("手机号"), view.error());
    }

    @Test
    void duplicateAccountStudentPairIsRejected() {
        StudentAccountImportResult result = service.preview(workbook(List.of(
            row("S001", "家长", "妈妈", PHONE_MUM, null),
            row("S001", "家长", "妈妈", PHONE_MUM, null)), null), INSTITUTION_ID);

        assertEquals(1, result.errorCount().intValue());
        assertTrue(result.rows().get(1).error().contains("完全重复"), result.rows().get(1).error());
    }

    @Test
    void twoStudentSelfAccountsForOneStudentAreRejected() {
        StudentAccountImportResult result = service.preview(workbook(List.of(
            row("S001", "学生本人", "小明", PHONE_MUM, "xiaoming"),
            row("S001", "学生本人", "小明", PHONE_DAD, "xiaoming2")), null), INSTITUTION_ID);

        assertEquals(1, result.errorCount().intValue());
        assertTrue(result.rows().get(1).error().contains("学生本人"), result.rows().get(1).error());
    }

    @Test
    void parentMayBindStudentAlreadyOwnedByAnotherAccount() {
        // S003 的 jp_student.user_id 已指向 99，家长账号绑他不该报错，只提示
        StudentAccountImportResult result = service.preview(workbook(List.of(
            row("S003", "家长", "妈妈", PHONE_MUM, null)), null), INSTITUTION_ID);

        assertEquals(0, result.errorCount().intValue());
        assertNotNull(result.rows().get(0).notice());
    }

    @Test
    void studentSelfAccountConflictingWithExistingOwnerIsRejected() {
        StudentAccountImportResult result = service.preview(workbook(List.of(
            row("S003", "学生本人", "小刚", PHONE_MUM, null)), null), INSTITUTION_ID);

        assertEquals(1, result.errorCount().intValue());
        assertTrue(result.rows().get(0).error().contains("已被其他账号归属"), result.rows().get(0).error());
    }

    @Test
    void blankTemplateHasNoDataRow() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.preview(new MockMultipartFile("file", "模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                service.buildTemplate()), INSTITUTION_ID));

        // 模板第一个工作表必须是数据页，否则客户照着填也导不进去
        assertTrue(exception.getMessage().contains("未读到有效数据行"), exception.getMessage());
    }

    @Test
    void rosterSkipsStudentsThatAlreadyHaveAnAccount() throws IOException {
        // S003 占了 jp_student.user_id（学生本人账号），S002 被家长账号绑过（jp_user_student_rel），
        // 两处任一命中都算"已有账号"，不该再让业务员填一遍
        when(userStudentRelMapper.selectList(any())).thenReturn(List.of(relation(9L, 11L)));

        List<List<String>> data = dataSheetRows(service.buildRoster(INSTITUTION_ID, null));

        assertEquals(1, data.size());
        assertEquals("S001", data.get(0).get(0));
        assertEquals("小明", data.get(0).get(1));
        // 后面几列必须留空：预填等于替业务员认了账号类型和手机号，错了也看不出来
        assertTrue(data.get(0).subList(2, 7).stream().allMatch(String::isEmpty), data.get(0).toString());
    }

    @Test
    void rosterFileIsDirectlyReuploadable() {
        MockMultipartFile roster = new MockMultipartFile("file", "待开账号学员名册.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            service.buildRoster(INSTITUTION_ID, null));

        // 名册原样传回来必须还能按同样的列位读回去，报的错只可能是"这一列你还没填"
        StudentAccountImportResult result = service.preview(roster, INSTITUTION_ID);

        // 名册里是 S001、S002（S003 已有学生本人账号被排除），两行的列位都必须和读入侧对齐
        assertEquals(2, result.total().intValue());
        assertEquals("S001", result.rows().get(0).studentNo());
        assertEquals("S002", result.rows().get(1).studentNo());
        assertTrue(result.rows().get(0).error().contains("账号类型"), result.rows().get(0).error());
    }

    @Test
    void nonXlsxFileIsRejected() {
        MockMultipartFile csv = new MockMultipartFile("file", "账号.csv", "text/csv",
            "S001,家长,妈妈,13800000001".getBytes());

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.preview(csv, INSTITUTION_ID));
        assertTrue(exception.getMessage().contains("xlsx"), exception.getMessage());
    }

    @Test
    void otherInstitutionIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.preview(workbook(List.of(row("S001", "家长", "妈妈", PHONE_MUM, null)), null), 2L));

        assertTrue(exception.getMessage().contains("当前账号所属机构"), exception.getMessage());
    }

    // ------------------------------------------------------------------ 落库

    @Test
    void commitCreatesOneAccountPerPhoneAndBindsEveryRow() {
        StudentAccountImportResult result = service.commit(workbook(List.of(
            row("S001", "家长", "妈妈", PHONE_MUM, null),
            row("S002", "家长", "妈妈", PHONE_MUM, null)), null), INSTITUTION_ID);

        assertEquals(1, result.createdCount().intValue());
        assertEquals(2, result.boundCount().intValue());
        assertEquals(0, result.skippedCount().intValue());
        verify(systemUserService, times(1)).createUser(any(UserCreateRequest.class));
        verify(userStudentRelMapper, times(2)).insert(any(UserStudentRelEntity.class));
        // 家长账号不改 jp_student.user_id，那是"学生本人"账号的归属
        verify(studentMapper, never()).updateById(any(StudentEntity.class));
        // 两个学员同类型，角色只补一次
        verify(userRoleRelMapper, times(1)).insert(any(UserRoleRelEntity.class));
        assertEquals(result.rows().get(0).userId(), result.rows().get(1).userId());
        assertNotNull(result.rows().get(0).userId());
    }

    @Test
    void studentSelfCommitWritesOwnership() {
        service.commit(workbook(List.of(row("S001", "学生本人", "小明", PHONE_MUM, "xiaoming")), null),
            INSTITUTION_ID);

        verify(studentMapper, times(1)).updateById(any(StudentEntity.class));
    }

    @Test
    void commitRejectsWholeBatchWhenAnyRowInvalid() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.commit(workbook(List.of(
                row("S001", "家长", "妈妈", PHONE_MUM, null),
                row("S999", "家长", "爸爸", PHONE_DAD, null)), null), INSTITUTION_ID));

        assertTrue(exception.getMessage().contains("整批未导入"), exception.getMessage());
        verify(systemUserService, never()).createUser(any());
        verify(userStudentRelMapper, never()).insert(any(UserStudentRelEntity.class));
    }

    @Test
    void existingPendingAccountIsReusedAndReportedAsSkipped() {
        when(userAccountMapper.selectList(any())).thenReturn(List.of(
            account(77L, PHONE_MUM, AuthInvitationService.STATUS_PENDING_ACTIVATION)));
        when(userStudentRelMapper.selectList(any())).thenReturn(List.of(relation(77L, 10L)));

        StudentAccountImportResult result = service.commit(workbook(List.of(
            row("S001", "家长", "妈妈", PHONE_MUM, null)), null), INSTITUTION_ID);

        assertEquals(0, result.createdCount().intValue());
        assertEquals(1, result.skippedCount().intValue());
        assertEquals(77L, result.rows().get(0).userId().longValue());
        assertTrue(result.rows().get(0).notice().contains("账号已存在"), result.rows().get(0).notice());
    }

    // ------------------------------------------------------------------ 批量发码

    @Test
    void activationCodesSkipAccountsThatAreNotPending() {
        when(userAccountMapper.selectById(101L)).thenReturn(
            account(101L, PHONE_MUM, AuthInvitationService.STATUS_PENDING_ACTIVATION));
        when(userAccountMapper.selectById(102L)).thenReturn(account(102L, PHONE_DAD, "active"));
        when(authInvitationService.createActivationCode(any())).thenReturn(
            new InvitationView(1L, "ACTSAMPLE", "ACTIVATE", INSTITUTION_ID, null, 101L, PHONE_MUM,
                null, null, 1, 0, null, "active", null, null));

        List<StudentAccountActivationView> views = service.createActivationCodes(
            new StudentAccountActivationRequest(List.of(101L, 102L, 103L), 3), INSTITUTION_ID);

        assertEquals(3, views.size());
        assertEquals("ACTSAMPLE", views.get(0).code());
        assertTrue(views.get(1).message().contains("已激活"), views.get(1).message());
        assertTrue(views.get(2).message().contains("不存在"), views.get(2).message());
        assertNull(views.get(2).code());
        verify(authInvitationService, times(1)).createActivationCode(any());
    }

    // ------------------------------------------------------------------ 造文件与造数据

    /** 模板列顺序：学员编号、学员姓名、账号类型、账号姓名、手机号、登录账号、备注 */
    private List<Object> row(String studentNo, String accountKind, String displayName,
                             String phone, String username) {
        return List.of(studentNo, "", accountKind, displayName,
            phone == null ? "" : phone, username == null ? "" : username, "");
    }

    /** numericPhone 非空时，第一行的手机号按数值单元格写入，模拟客户直接敲数字后 Excel 的存法 */
    private MockMultipartFile workbook(List<List<Object>> rows, String numericPhone) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("账号导入");
            Row header = sheet.createRow(0);
            String[] titles = {"学员编号*", "学员姓名(核对)", "账号类型*", "账号姓名*",
                "手机号", "登录账号(留空自动取)", "备注"};
            for (int index = 0; index < titles.length; index++) {
                header.createCell(index).setCellValue(titles[index]);
            }
            for (int index = 0; index < rows.size(); index++) {
                Row row = sheet.createRow(index + 1);
                List<Object> values = rows.get(index);
                for (int column = 0; column < values.size(); column++) {
                    Cell cell = row.createCell(column);
                    cell.setCellValue(String.valueOf(values.get(column)));
                }
                if (numericPhone != null && index == 0) {
                    row.getCell(4).setCellValue(Double.parseDouble(numericPhone));
                }
            }
            book.write(out);
            return new MockMultipartFile("file", "账号导入.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** 读回第一个工作表的数据行（跳表头），列位不对断言就会挂——导出与解析必须共用同一套列顺序 */
    private List<List<String>> dataSheetRows(byte[] xlsx) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = book.getSheetAt(0);
            List<List<String>> rows = new ArrayList<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (int column = 0; column < 7; column++) {
                    Cell cell = row.getCell(column);
                    cells.add(cell == null ? "" : cell.toString().trim());
                }
                rows.add(cells);
            }
            return rows;
        }
    }

    private void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(1L, INSTITUTION_ID, "admin",
            Set.of("ADMIN"), Set.of("education:write"), false, null);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }

    private StudentEntity student(Long id, String studentNo, String studentName, Long userId) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setInstitutionId(INSTITUTION_ID);
        student.setStudentNo(studentNo);
        student.setStudentName(studentName);
        student.setUserId(userId);
        return student;
    }

    private RoleEntity role(Long id, String roleCode) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setRoleCode(roleCode);
        return role;
    }

    private UserAccountEntity account(Long id, String username, String status) {
        UserAccountEntity account = new UserAccountEntity();
        account.setId(id);
        account.setInstitutionId(INSTITUTION_ID);
        account.setUsername(username);
        account.setStatus(status);
        return account;
    }

    private UserStudentRelEntity relation(Long userId, Long studentId) {
        UserStudentRelEntity relation = new UserStudentRelEntity();
        relation.setInstitutionId(INSTITUTION_ID);
        relation.setUserId(userId);
        relation.setStudentId(studentId);
        return relation;
    }

    private UserView userView(Long id, UserCreateRequest request) {
        return new UserView(id, request.institutionId(), request.username(), request.displayName(),
            request.phone(), request.status(), false, null, request.remark(), List.of(), List.of());
    }
}
