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
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.dto.StudentCreateRequest;
import com.cornerstone.module.education.dto.XlsxImportResult;
import com.cornerstone.module.education.dto.XlsxImportRowView;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.service.StudentService;
import com.cornerstone.module.education.service.impl.StudentImportServiceImpl;
import com.cornerstone.module.education.service.support.DictValueValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * 学员档案批量导入：用真实 xlsx 走一遍解析、校验与落库。
 *
 * <p>与教师导入的测试同口径：列顺序、数值单元格、模板首表这几处只有真文件才能验出来。</p>
 */
class StudentImportServiceImplTest {

    private static final long INSTITUTION_ID = 1L;
    /** 模板列顺序：学号、姓名、性别、出生日期、监护人、监护人电话、状态、入学日期、备注 */
    private static final String[] TITLES = {
        "学号", "学员姓名*", "性别", "出生日期", "监护人姓名", "监护人电话", "状态", "入学日期", "备注"
    };
    private static final Map<String, List<String>> DICT = Map.of(
        "gender", List.of("male", "female", "unknown"),
        "student_status", List.of("studying", "suspended", "graduated", "withdrawn"));

    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final StudentService studentService = mock(StudentService.class);
    private final DictValueValidator dictValueValidator = mock(DictValueValidator.class);
    private final SystemDictService systemDictService = mock(SystemDictService.class);

    private final AtomicLong nextStudentId = new AtomicLong(100L);

    private StudentImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentImportServiceImpl(studentMapper, studentService,
            dictValueValidator, systemDictService);
        authenticate();
        when(studentMapper.selectList(any())).thenReturn(List.of());
        when(dictValueValidator.valueByLabel("gender"))
            .thenReturn(Map.of("男", "male", "女", "female", "未知", "unknown"));
        when(dictValueValidator.valueByLabel("student_status"))
            .thenReturn(Map.of("在读", "studying", "停课", "suspended", "结业", "graduated", "退学", "withdrawn"));
        when(dictValueValidator.validate(any(), any(), any())).thenAnswer(invocation -> {
            String type = invocation.getArgument(0);
            String label = invocation.getArgument(1);
            String value = invocation.getArgument(2);
            if (value == null || value.isBlank()) {
                return null;
            }
            List<String> allowed = DICT.getOrDefault(type, List.of());
            if (!allowed.contains(value.trim())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    label + "取值无效，可选值：" + String.join("/", allowed));
            }
            return value.trim();
        });
        when(systemDictService.listItems(any())).thenAnswer(invocation ->
            DICT.getOrDefault(invocation.getArgument(0), List.of()).stream()
                .map(value -> new DictItemView(1L, 1L, value, value, value, 1, "active", null))
                .toList());
        when(studentService.createStudent(any())).thenAnswer(invocation -> {
            StudentCreateRequest request = invocation.getArgument(0);
            StudentEntity entity = new StudentEntity();
            entity.setId(nextStudentId.getAndIncrement());
            entity.setInstitutionId(request.institutionId());
            entity.setStudentNo(request.studentNo() == null ? "XS-auto-1" : request.studentNo());
            entity.setStudentName(request.studentName());
            return entity;
        });
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 校验

    @Test
    void validRowsPassPreviewWithoutWriting() {
        XlsxImportResult result = service.preview(workbook(List.of(
            row("S001", "张三", "male", "2015-03-05", "张父", "13800000001", "", "2026-03-05", ""),
            row("", "李四", "女", "2016/3/5", "", "", "在读", "", "备注"))));

        assertEquals(2, result.total().intValue());
        assertEquals(2, result.validCount().intValue(), result.rows().get(1).error());
        assertEquals(0, result.errorCount().intValue());
        verify(studentService, never()).createStudent(any());
    }

    @Test
    void missingNameIsRejected() {
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("S001", "", "", "", "", "", "", "", "")))).rows().get(0);

        assertFalse(view.valid());
        assertTrue(view.error().contains("学员姓名不能为空"), view.error());
    }

    @Test
    void blankStudentNoIsAllowedBecauseCodeRuleFillsItOnCommit() {
        // 批量路径是"留空即补号"的合法入口；预览通过、结果里回填真实学号
        XlsxImportResult result = service.commit(workbook(List.of(
            row("", "张三", "", "", "", "", "", "", ""))));

        assertEquals(1, result.createdCount().intValue());
        assertEquals("XS-auto-1", result.rows().get(0).cells().get("studentNo"));

        ArgumentCaptor<StudentCreateRequest> created = ArgumentCaptor.forClass(StudentCreateRequest.class);
        verify(studentService).createStudent(created.capture());
        assertNull(created.getValue().studentNo());
        assertEquals(INSTITUTION_ID, created.getValue().institutionId().longValue());
    }

    @Test
    void existingStudentNoIsSkippedNotOverwritten() {
        when(studentMapper.selectList(any())).thenReturn(List.of(student(77L, "S001")));

        XlsxImportRowView view = service.preview(workbook(List.of(
            row("S001", "张三", "", "", "", "", "", "", "")))).rows().get(0);

        assertTrue(view.valid(), view.error());
        assertTrue(view.notice().contains("跳过新建"), view.notice());
    }

    @Test
    void duplicateStudentNoWithinOneFileIsRejected() {
        XlsxImportResult result = service.preview(workbook(List.of(
            row("S001", "张三", "", "", "", "", "", "", ""),
            row("S001", "李四", "", "", "", "", "", "", ""))));

        assertEquals(1, result.errorCount().intValue());
        assertTrue(result.rows().get(1).error().contains("出现多行"), result.rows().get(1).error());
    }

    @Test
    void invalidDictValueBecomesRowErrorNotBatchFailure() {
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("S001", "张三", "MALE", "", "", "", "", "", "")))).rows().get(0);

        assertFalse(view.valid());
        assertTrue(view.error().contains("性别取值无效"), view.error());
        assertTrue(view.error().contains("male"), view.error());
    }

    /** 导出 → 改 → 回灌是本特性的基本用法：中文条目名归一成 item_value 后再走校验和落库 */
    @Test
    void chineseDictNamesAreNormalizedToItemValues() {
        ArgumentCaptor<StudentCreateRequest> created = ArgumentCaptor.forClass(StudentCreateRequest.class);

        service.commit(workbook(List.of(
            row("S001", "张三", "女", "", "", "", "停课", "", ""))));

        verify(studentService).createStudent(created.capture());
        assertEquals("female", created.getValue().gender());
        assertEquals("suspended", created.getValue().status());
    }

    @Test
    void blankStatusEchoesStudyingDefault() {
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("S001", "张三", "", "", "", "", "", "", "")))).rows().get(0);

        assertEquals("studying", view.cells().get("status"));
    }

    @Test
    void malformedAndFutureDatesAreRejected() {
        XlsxImportResult result = service.preview(workbook(List.of(
            row("S001", "张三", "", "20150305X", "", "", "", "20260305A", ""),
            row("S002", "李四", "", java.time.LocalDate.now().plusDays(1).toString(), "", "", "", "", ""))));

        assertTrue(result.rows().get(0).error().contains("出生日期格式不对"), result.rows().get(0).error());
        assertTrue(result.rows().get(0).error().contains("入学日期格式不对"), result.rows().get(0).error());
        assertTrue(result.rows().get(1).error().contains("出生日期不能晚于当前日期"), result.rows().get(1).error());
    }

    @Test
    void numericStudentNoCellIsReadBackAsPlainDigits() {
        XlsxImportResult result = service.preview(workbook(
            List.of(row("20260001", "张三", "", "", "", "", "", "", "")), 0));

        assertEquals("20260001", result.rows().get(0).cells().get("studentNo"));
        assertEquals(0, result.errorCount().intValue(), result.rows().get(0).error());
    }

    // ------------------------------------------------------------------ 落库

    @Test
    void commitRejectsWholeBatchWhenAnyRowInvalid() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.commit(workbook(List.of(
                row("S001", "张三", "", "", "", "", "", "", ""),
                row("S002", "", "", "", "", "", "", "", "")))));

        assertTrue(exception.getMessage().contains("整批未导入"), exception.getMessage());
        verify(studentService, never()).createStudent(any());
    }

    @Test
    void commitSkipsExistingStudentNo() {
        when(studentMapper.selectList(any())).thenReturn(List.of(student(77L, "S001")));

        XlsxImportResult result = service.commit(workbook(List.of(
            row("S001", "张三", "", "", "", "", "", "", ""),
            row("S003", "王五", "", "", "", "", "", "", ""))));

        assertEquals(1, result.createdCount().intValue());
        assertEquals(1, result.skippedCount().intValue());
        verify(studentService, times(1)).createStudent(any());
    }

    // ------------------------------------------------------------------ 模板与登录态

    @Test
    void emptyTemplateIsRejectedInsteadOfImportingNothing() {
        MockMultipartFile template = new MockMultipartFile("file", "学员档案批量导入模板.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", service.buildTemplate());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(template));

        assertTrue(exception.getMessage().contains("未读到有效数据行"), exception.getMessage());
    }

    @Test
    void templateFirstSheetMustBeTheDataSheet() throws IOException {
        List<List<String>> dataRows = readDataSheet(service.buildTemplate());

        assertEquals(TITLES.length, dataRows.get(0).size());
        for (int index = 0; index < TITLES.length; index++) {
            assertEquals(TITLES[index], dataRows.get(0).get(index));
        }
    }

    @Test
    void templateDropdownsComeFromTheDictNotFromHardcodedValues() throws IOException {
        String guide = readGuideSheet(service.buildTemplate());

        assertTrue(guide.contains("male/female/unknown"), guide);
        assertTrue(guide.contains("studying/suspended/graduated/withdrawn"), guide);
    }

    @Test
    void nonXlsxFileIsRejected() {
        MockMultipartFile csv = new MockMultipartFile("file", "学员.csv", "text/csv",
            "S001,张三".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(csv));

        assertTrue(exception.getMessage().contains("xlsx"), exception.getMessage());
    }

    @Test
    void accountWithoutInstitutionCannotImport() {
        authenticate(null);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.preview(workbook(List.of(
                row("S001", "张三", "", "", "", "", "", "", "")))));

        assertTrue(exception.getMessage().contains("未绑定机构"), exception.getMessage());
    }

    // ------------------------------------------------------------------ 造文件与造数据

    private List<Object> row(String studentNo, String studentName, String gender, String birthday,
                             String guardianName, String guardianPhone, String status,
                             String enrolledAt, String remark) {
        return List.of(studentNo, studentName, gender, birthday, guardianName, guardianPhone,
            status, enrolledAt, remark);
    }

    private MockMultipartFile workbook(List<List<Object>> rows) {
        return workbook(rows, null);
    }

    /** numericColumn 非空时把该列按数值单元格写入，模拟客户直接敲数字后 Excel 的存法 */
    private MockMultipartFile workbook(List<List<Object>> rows, Integer numericColumn) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("学员档案导入");
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
            return new MockMultipartFile("file", "学员档案导入.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private List<List<String>> readDataSheet(byte[] xlsx) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = book.getSheetAt(0);
            List<List<String>> rows = new ArrayList<>();
            Row header = sheet.getRow(0);
            List<String> cells = new ArrayList<>();
            for (int column = 0; column < TITLES.length; column++) {
                Cell cell = header.getCell(column);
                cells.add(cell == null ? "" : cell.toString().trim());
            }
            rows.add(cells);
            return rows;
        }
    }

    private String readGuideSheet(byte[] xlsx) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            StringBuilder text = new StringBuilder();
            Sheet sheet = book.getSheet("填写说明");
            for (int index = 0; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row != null && row.getCell(0) != null) {
                    text.append(row.getCell(0).toString()).append('\n');
                }
            }
            return text.toString();
        }
    }

    private void authenticate() {
        authenticate(INSTITUTION_ID);
    }

    private void authenticate(Long orgId) {
        AuthenticatedUser user = new AuthenticatedUser(1L, orgId, "admin",
            Set.of("EDU"), Set.of("education:profile:write"), false, null);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", List.of()));
    }

    private StudentEntity student(Long id, String studentNo) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setInstitutionId(INSTITUTION_ID);
        student.setStudentNo(studentNo);
        return student;
    }
}
