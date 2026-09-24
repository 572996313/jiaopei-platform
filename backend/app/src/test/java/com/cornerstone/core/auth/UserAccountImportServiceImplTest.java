package com.cornerstone.core.auth;

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
import com.cornerstone.core.auth.dto.UserAccountImportResult;
import com.cornerstone.core.auth.dto.UserAccountImportRowView;
import com.cornerstone.core.auth.dto.UserCreateRequest;
import com.cornerstone.core.auth.dto.UserRoleUpdateRequest;
import com.cornerstone.core.auth.dto.UserView;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.service.PasswordValidator;
import com.cornerstone.core.auth.service.SystemUserService;
import com.cornerstone.core.auth.service.impl.UserAccountImportServiceImpl;
import com.cornerstone.core.common.exception.BusinessException;
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
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 用户账号批量导入：用真实 xlsx 走一遍解析、校验与落库。
 *
 * <p>刻意用 POI 造文件而不是直接喂 DTO——列顺序、数值单元格、模板首表这三处
 * 是客户手填最容易坏的地方，只有在真文件上才验得出来。</p>
 */
class UserAccountImportServiceImplTest {

    private static final long INSTITUTION_ID = 1L;
    private static final long OTHER_INSTITUTION_ID = 2L;
    private static final String OK_PASSWORD = "Passw0rd!";
    /** 模板列顺序：登录账号、显示名称、初始口令、手机号、角色编码、状态、备注 */
    private static final String[] TITLES = {
        "登录账号*", "显示名称*", "初始口令*", "手机号", "角色编码*", "状态", "备注"
    };

    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final SystemUserService systemUserService = mock(SystemUserService.class);
    private final PasswordValidator passwordValidator = mock(PasswordValidator.class);

    private final AtomicLong nextUserId = new AtomicLong(100L);

    private UserAccountImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAccountImportServiceImpl(userAccountMapper, roleMapper,
            systemUserService, passwordValidator);
        authenticate();
        when(roleMapper.selectList(any())).thenReturn(List.of(
            role(10L, "PLATFORM_ADMIN"), role(11L, "AUDITOR")));
        when(userAccountMapper.selectList(any())).thenReturn(List.of());
        // 口令策略按"长度够且字母数字都有"模拟，避免测试跟着系统参数漂移
        when(passwordValidator.meetsRequirements(any())).thenAnswer(invocation -> {
            String password = invocation.getArgument(0);
            return password != null && password.length() >= 8
                && password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
        });
        when(systemUserService.createUser(any())).thenAnswer(invocation ->
            userView(nextUserId.getAndIncrement(), invocation.getArgument(0)));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 校验

    @Test
    void validRowsPassPreviewWithoutWriting() {
        UserAccountImportResult result = service.preview(workbook(List.of(
            row("teacher01", "张老师", OK_PASSWORD, "13800000001", "PLATFORM_ADMIN", "", ""),
            row("13800000002", "李老师", OK_PASSWORD, "", "PLATFORM_ADMIN,AUDITOR", "disabled", ""))));

        assertEquals(2, result.total().intValue());
        assertEquals(2, result.validCount().intValue());
        assertEquals(0, result.errorCount().intValue());
        verify(systemUserService, never()).createUser(any());
    }

    @Test
    void blankInitialPasswordIsRejectedNotSilentlyDefaults() {
        // 单个建号那条路径对空口令有公共兜底口令，批量必须拦住，否则一整批人共用同一个号
        UserAccountImportRowView view = service.preview(workbook(List.of(
            row("staff01", "小王", "", "", "PLATFORM_ADMIN", "", "")))).rows().get(0);

        assertFalse(view.valid(), "空口令不能算校验通过");
        assertTrue(view.error().contains("初始口令不能为空"), view.error());
    }

    @Test
    void weakInitialPasswordIsRejected() {
        UserAccountImportRowView view = service.preview(workbook(List.of(
            row("staff01", "小王", "abcdefgh", "", "PLATFORM_ADMIN", "", "")))).rows().get(0);

        assertTrue(view.error().contains("密码策略"), view.error());
    }

    @Test
    void unknownRoleCodeIsRejectedAndAvailableCodesListed() {
        UserAccountImportRowView view = service.preview(workbook(List.of(
            row("staff01", "小王", OK_PASSWORD, "", "BOSS", "", "")))).rows().get(0);

        assertTrue(view.error().contains("未找到角色编码 BOSS"), view.error());
        assertTrue(view.error().contains("PLATFORM_ADMIN"), "报错要给出可用编码：" + view.error());
    }

    @Test
    void usernameTakenByOtherInstitutionIsAnErrorNotASkip() {
        when(userAccountMapper.selectList(any())).thenReturn(List.of(
            account(77L, OTHER_INSTITUTION_ID, "staff01")));

        UserAccountImportRowView view = service.preview(workbook(List.of(
            row("staff01", "小王", OK_PASSWORD, "", "PLATFORM_ADMIN", "", "")))).rows().get(0);

        // 同号跨机构时不能说"已存在所以跳过"——那会让操作员以为账号在自己名下了
        assertTrue(view.error().contains("其他机构"), view.error());
        assertNull(view.userId());
    }

    @Test
    void usernameTakenByOwnInstitutionIsSkipped() {
        when(userAccountMapper.selectList(any())).thenReturn(List.of(
            account(77L, INSTITUTION_ID, "staff01")));

        UserAccountImportRowView view = service.preview(workbook(List.of(
            row("staff01", "小王", OK_PASSWORD, "", "PLATFORM_ADMIN", "", "")))).rows().get(0);

        assertTrue(view.valid(), view.error());
        assertTrue(view.notice().contains("跳过新建"), view.notice());
        assertEquals(77L, view.userId().longValue());
    }

    @Test
    void duplicateUsernameWithinOneFileIsRejected() {
        UserAccountImportResult result = service.preview(workbook(List.of(
            row("staff01", "小王", OK_PASSWORD, "", "PLATFORM_ADMIN", "", ""),
            row("staff01", "小李", OK_PASSWORD, "", "PLATFORM_ADMIN", "", ""))));

        assertEquals(1, result.errorCount().intValue());
        assertTrue(result.rows().get(1).error().contains("出现多行"), result.rows().get(1).error());
    }

    @Test
    void invalidStatusIsRejected() {
        UserAccountImportRowView view = service.preview(workbook(List.of(
            row("staff01", "小王", OK_PASSWORD, "", "PLATFORM_ADMIN", "enabled", "")))).rows().get(0);

        assertTrue(view.error().contains("状态只能填"), view.error());
    }

    @Test
    void blankStatusEchoesAsActive() {
        UserAccountImportRowView view = service.preview(workbook(List.of(
            row("staff01", "小王", OK_PASSWORD, "", "PLATFORM_ADMIN", "", "")))).rows().get(0);

        assertEquals("active", view.status());
    }

    @Test
    void numericPhoneCellIsReadBackAsPlainDigits() {
        // 客户直接敲数字，Excel 存成数值单元格，读回来不能是科学计数法
        UserAccountImportResult result = service.preview(workbook(
            List.of(row("staff01", "小王", OK_PASSWORD, "13800000001", "PLATFORM_ADMIN", "", "")), 3));

        assertEquals("13800000001", result.rows().get(0).phone(), result.rows().get(0).phone());
        assertEquals(0, result.errorCount().intValue(), result.rows().get(0).error());
    }

    @Test
    void malformedPhoneIsRejected() {
        UserAccountImportRowView view = service.preview(workbook(List.of(
            row("staff01", "小王", OK_PASSWORD, "138", "PLATFORM_ADMIN", "", "")))).rows().get(0);

        assertTrue(view.error().contains("手机号"), view.error());
    }

    // ------------------------------------------------------------------ 落库

    @Test
    void commitCreatesAccountsAndBindsResolvedRoleIds() {
        UserAccountImportResult result = service.commit(workbook(List.of(
            row("staff01", "小王", OK_PASSWORD, "13800000001", "PLATFORM_ADMIN,AUDITOR", "", "备注"),
            row("staff02", "小李", OK_PASSWORD, "", "AUDITOR", "disabled", ""))));

        assertEquals(2, result.createdCount().intValue());
        assertEquals(0, result.skippedCount().intValue());
        assertNotNull(result.rows().get(0).userId());

        ArgumentCaptor<UserCreateRequest> created = ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(systemUserService, times(2)).createUser(created.capture());
        // 机构来自登录态而不是文件：模板里根本没有这一列
        assertEquals(INSTITUTION_ID, created.getAllValues().get(0).institutionId().longValue());
        assertEquals(OK_PASSWORD, created.getAllValues().get(0).initialPassword());
        assertEquals("active", created.getAllValues().get(0).status());
        assertEquals("disabled", created.getAllValues().get(1).status());

        ArgumentCaptor<UserRoleUpdateRequest> roles = ArgumentCaptor.forClass(UserRoleUpdateRequest.class);
        verify(systemUserService, times(2)).updateRoles(any(), roles.capture());
        assertEquals(List.of(10L, 11L), roles.getAllValues().get(0).roleIds());
        assertEquals(List.of(11L), roles.getAllValues().get(1).roleIds());
    }

    @Test
    void commitSkipsExistingOwnInstitutionAccount() {
        when(userAccountMapper.selectList(any())).thenReturn(List.of(
            account(77L, INSTITUTION_ID, "staff01")));

        UserAccountImportResult result = service.commit(workbook(List.of(
            row("staff01", "小王", OK_PASSWORD, "", "PLATFORM_ADMIN", "", ""),
            row("staff02", "小李", OK_PASSWORD, "", "PLATFORM_ADMIN", "", ""))));

        assertEquals(1, result.createdCount().intValue());
        assertEquals(1, result.skippedCount().intValue());
        verify(systemUserService, times(1)).createUser(any());
        assertEquals(77L, result.rows().get(0).userId().longValue());
    }

    @Test
    void commitRejectsWholeBatchWhenAnyRowInvalid() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.commit(workbook(List.of(
                row("staff01", "小王", OK_PASSWORD, "", "PLATFORM_ADMIN", "", ""),
                row("staff02", "小李", "", "", "PLATFORM_ADMIN", "", "")))));

        assertTrue(exception.getMessage().contains("整批未导入"), exception.getMessage());
        verify(systemUserService, never()).createUser(any());
    }

    // ------------------------------------------------------------------ 文件与登录态

    @Test
    void emptyTemplateIsRejectedInsteadOfImportingNothing() {
        // 模板首表只有表头，原样传回来必须给出"没数据"而不是静默成功
        MockMultipartFile template = new MockMultipartFile("file", "账号批量导入模板.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", service.buildTemplate());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(template));

        assertTrue(exception.getMessage().contains("未读到有效数据行"), exception.getMessage());
    }

    @Test
    void templateFirstSheetMustBeTheDataSheet() throws IOException {
        // 说明页排在前面的话，客户照着填就永远导不进去
        List<List<String>> dataRows = readDataSheet(service.buildTemplate());

        assertEquals(7, dataRows.get(0).size());
        assertEquals(TITLES.length, dataRows.get(0).size());
        for (int index = 0; index < TITLES.length; index++) {
            assertEquals(TITLES[index], dataRows.get(0).get(index));
        }
    }

    @Test
    void nonXlsxFileIsRejected() {
        MockMultipartFile csv = new MockMultipartFile("file", "账号.csv", "text/csv",
            "staff01,小王,Passw0rd!".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(csv));

        assertTrue(exception.getMessage().contains("xlsx"), exception.getMessage());
    }

    @Test
    void accountWithoutInstitutionCannotImport() {
        authenticate(null);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.preview(workbook(List.of(
                row("staff01", "小王", OK_PASSWORD, "", "PLATFORM_ADMIN", "", "")))));

        assertTrue(exception.getMessage().contains("未绑定机构"), exception.getMessage());
    }

    // ------------------------------------------------------------------ 造文件与造数据

    private List<Object> row(String username, String displayName, String initialPassword,
                             String phone, String roleCodes, String status, String remark) {
        return List.of(username, displayName, initialPassword, phone, roleCodes, status, remark);
    }

    /** numericColumn 非空时把该列按数值单元格写入，模拟客户直接敲数字后 Excel 的存法 */
    private MockMultipartFile workbook(List<List<Object>> rows) {
        return workbook(rows, null);
    }

    private MockMultipartFile workbook(List<List<Object>> rows, Integer numericColumn) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("账号导入");
            Row header = sheet.createRow(0);
            for (int index = 0; index < TITLES.length; index++) {
                header.createCell(index).setCellValue(TITLES[index]);
            }
            for (int index = 0; index < rows.size(); index++) {
                Row row = sheet.createRow(index + 1);
                List<Object> values = rows.get(index);
                for (int column = 0; column < values.size(); column++) {
                    row.createCell(column).setCellValue(String.valueOf(values.get(column)));
                }
                if (numericColumn != null) {
                    row.getCell(numericColumn)
                        .setCellValue(Double.parseDouble(String.valueOf(values.get(numericColumn))));
                }
            }
            book.write(out);
            return new MockMultipartFile("file", "账号导入.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** 读回第一个工作表：首行必须是表头，列位与解析侧完全一致 */
    private List<List<String>> readDataSheet(byte[] xlsx) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = book.getSheetAt(0);
            List<List<String>> rows = new ArrayList<>();
            for (int index = 0; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (int column = 0; column < TITLES.length; column++) {
                    Cell cell = row.getCell(column);
                    cells.add(cell == null ? "" : cell.toString().trim());
                }
                rows.add(cells);
            }
            return rows;
        }
    }

    private void authenticate() {
        authenticate(INSTITUTION_ID);
    }

    private void authenticate(Long orgId) {
        AuthenticatedUser user = new AuthenticatedUser(1L, orgId, "admin",
            Set.of("PLATFORM_ADMIN"), Set.of("system:user:manage"), false, null);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }

    private RoleEntity role(Long id, String roleCode) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setRoleCode(roleCode);
        return role;
    }

    private UserAccountEntity account(Long id, Long institutionId, String username) {
        UserAccountEntity account = new UserAccountEntity();
        account.setId(id);
        account.setInstitutionId(institutionId);
        account.setUsername(username);
        account.setStatus("active");
        return account;
    }

    private UserView userView(Long id, UserCreateRequest request) {
        return new UserView(id, request.institutionId(), request.username(), request.displayName(),
            request.phone(), request.status(), true, null, request.remark(), List.of(), List.of());
    }
}
