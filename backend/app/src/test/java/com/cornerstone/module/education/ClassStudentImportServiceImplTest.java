package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.XlsxImportResult;
import com.cornerstone.module.education.dto.XlsxImportRowView;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.support.DictValueValidator;
import com.cornerstone.module.education.teachingclass.dto.ClassStudentCreateRequest;
import com.cornerstone.module.education.teachingclass.service.ClassStudentService;
import com.cornerstone.module.education.teachingclass.service.impl.ClassStudentImportServiceImpl;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * 分班批量导入：管理员最常做的一件事是把一份名单塞进一个班。
 *
 * <p>重点在三个口径：班级编码跨校区可重码（可选用校区编码消歧）、已在班是跳过而不是报错、
 * 容量按"现有在班 + 本文件已接受"投影预检。</p>
 */
class ClassStudentImportServiceImplTest {

    private static final long INSTITUTION_ID = 1L;
    private static final long CAMPUS_A_ID = 20L;
    private static final long CAMPUS_B_ID = 21L;
    private static final long CLASS_A_ID = 30L;
    private static final long CLASS_B_ID = 31L;
    private static final long STUDENT_ZHANG_ID = 40L;
    private static final long STUDENT_LI_ID = 41L;
    private static final long STUDENT_WANG_ID = 42L;
    /** 模板列顺序：班级编码、校区编码、学号、姓名、来源渠道、入班日期、备注 */
    private static final String[] TITLES = {
        "班级编码*", "校区编码", "学号*", "学员姓名(核对)", "来源渠道", "入班日期", "备注"
    };
    private static final List<String> CHANNEL_VALUES =
        List.of("front_desk", "consultant", "online", "referral");

    private final TeachingClassMapper teachingClassMapper = mock(TeachingClassMapper.class);
    private final ClassStudentMapper classStudentMapper = mock(ClassStudentMapper.class);
    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final CampusMapper campusMapper = mock(CampusMapper.class);
    private final ClassStudentService classStudentService = mock(ClassStudentService.class);
    private final DictValueValidator dictValueValidator = mock(DictValueValidator.class);
    private final SystemDictService systemDictService = mock(SystemDictService.class);

    private ClassStudentImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClassStudentImportServiceImpl(teachingClassMapper, classStudentMapper,
            studentMapper, campusMapper, classStudentService, dictValueValidator, systemDictService);
        authenticate();
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(
            teachingClass(CLASS_A_ID, CAMPUS_A_ID, "K001", "一班", 2),
            teachingClass(CLASS_B_ID, CAMPUS_B_ID, "K001", "二班", 0)));
        when(studentMapper.selectList(any())).thenReturn(List.of(
            student(STUDENT_ZHANG_ID, "S001", "张三"),
            student(STUDENT_LI_ID, "S002", "李四"),
            student(STUDENT_WANG_ID, "S003", "王五")));
        when(campusMapper.selectList(any())).thenReturn(List.of(
            campus(CAMPUS_A_ID, "CA"), campus(CAMPUS_B_ID, "CB")));
        when(classStudentMapper.selectList(any())).thenReturn(List.of());
        when(dictValueValidator.valueByLabel("enrollment_channel")).thenReturn(Map.of(
            "前台报名", "front_desk", "顾问录入", "consultant",
            "线上线索", "online", "转介绍", "referral"));
        when(dictValueValidator.validate(any(), any(), any())).thenAnswer(invocation -> {
            String value = invocation.getArgument(2);
            if (value == null || value.isBlank()) {
                return null;
            }
            if (!CHANNEL_VALUES.contains(value.trim())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "来源渠道取值无效，可选值：前台报名(front_desk)/顾问录入(consultant)/"
                        + "线上线索(online)/转介绍(referral)");
            }
            return value.trim();
        });
        when(systemDictService.listItems("enrollment_channel")).thenAnswer(invocation ->
            CHANNEL_VALUES.stream()
                .map(value -> new DictItemView(1L, 1L, value, value, value, 1, "active", null))
                .toList());
        when(classStudentService.createClassStudent(any())).thenAnswer(invocation -> {
            ClassStudentCreateRequest request = invocation.getArgument(0);
            ClassStudentEntity entity = new ClassStudentEntity();
            entity.setId(100L);
            entity.setInstitutionId(request.institutionId());
            entity.setClassId(request.classId());
            entity.setStudentId(request.studentId());
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
            row("K001", "CB", "S001", "张三", "转介绍", "2026-03-05", ""),
            row("K001", "CB", "S003", "", "", "", ""))));

        assertEquals(2, result.validCount().intValue(), result.rows().get(0).error());
        assertEquals(0, result.errorCount().intValue());
        verify(classStudentService, never()).createClassStudent(any());
    }

    @Test
    void unknownClassCodeIsRejected() {
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("K999", "", "S001", "", "", "", "")))).rows().get(0);

        assertFalse(view.valid());
        assertTrue(view.error().contains("未找到班级编码 K999"), view.error());
    }

    @Test
    void unknownStudentNoIsRejectedWithGuidance() {
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("K001", "CB", "S999", "", "", "", "")))).rows().get(0);

        assertTrue(view.error().contains("未找到学号 S999"), view.error());
        assertTrue(view.error().contains("学员管理页"), view.error());
    }

    /** 姓名只做核对：Excel 串行时要把错误摆在预览里，而不是把 A 塞进 B 的班 */
    @Test
    void studentNameMismatchIsRejected() {
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("K001", "CB", "S001", "李四", "", "", "")))).rows().get(0);

        assertTrue(view.error().contains("对应学员姓名为「张三」"), view.error());
    }

    /**
     * 班级编码的唯一键是「校区ID + 编码」：同一编码跨校区存在时必须填校区编码消歧，
     * 不允许替操作员猜一个。
     */
    @Test
    void ambiguousClassCodeRequiresCampusCode() {
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("K001", "", "S001", "", "", "", "")))).rows().get(0);

        assertFalse(view.valid());
        assertTrue(view.error().contains("多个校区都存在"), view.error());
        assertTrue(view.error().contains("CA"), view.error());
        assertTrue(view.error().contains("CB"), view.error());
    }

    @Test
    void campusCodeResolvesAmbiguousClassCode() {
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("K001", "CB", "S001", "", "", "", "")))).rows().get(0);

        assertTrue(view.valid(), view.error());
    }

    @Test
    void campusCodeMustMatchTheChosenClass() {
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("K001", "CA", "S001", "", "", "", "")))).rows().get(0);

        // CA 下确实有 K001（一班），所以这里应当通过；用不存在的校区验证不一致的分支
        assertTrue(view.valid(), view.error());

        TeachingClassEntity onlyInB = teachingClass(32L, CAMPUS_B_ID, "K900", "九班", 0);
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(onlyInB));
        XlsxImportRowView mismatch = service.preview(workbook(List.of(
            row("K900", "CA", "S001", "", "", "", "")))).rows().get(0);
        assertTrue(mismatch.error().contains("与填写的校区编码 CA 不一致"), mismatch.error());
    }

    @Test
    void duplicatePairWithinOneFileIsRejected() {
        XlsxImportResult result = service.preview(workbook(List.of(
            row("K001", "CB", "S001", "", "", "", ""),
            row("K001", "CB", "S001", "", "", "", ""))));

        assertEquals(1, result.errorCount().intValue());
        assertTrue(result.rows().get(1).error().contains("出现多行"), result.rows().get(1).error());
    }

    @Test
    void existingRosterIsSkippedNotRejected() {
        when(classStudentMapper.selectList(any())).thenReturn(List.of(
            roster(CLASS_A_ID, STUDENT_LI_ID, "active")));

        XlsxImportRowView view = service.preview(workbook(List.of(
            row("K001", "CA", "S002", "李四", "", "", "")))).rows().get(0);

        assertTrue(view.valid(), view.error());
        assertTrue(view.notice().contains("跳过"), view.notice());
    }

    /**
     * 容量投影：一班上限 2、现有 1 人（李四）。张三占掉最后一个名额后，王五必须被拦下，
     * 而且报错要带上容量与当前人数，操作员才知道该去扩班还是换班。
     */
    @Test
    void capacityProjectionRejectsRowsBeyondTheLimit() {
        when(classStudentMapper.selectList(any())).thenReturn(List.of(
            roster(CLASS_A_ID, STUDENT_LI_ID, "active")));

        XlsxImportResult result = service.preview(workbook(List.of(
            row("K001", "CA", "S001", "", "", "", ""),
            row("K001", "CA", "S003", "", "", "", ""))));

        assertTrue(result.rows().get(0).valid(), result.rows().get(0).error());
        assertFalse(result.rows().get(1).valid());
        assertTrue(result.rows().get(1).error().contains("容量已满"), result.rows().get(1).error());
        assertTrue(result.rows().get(1).error().contains("上限 2"), result.rows().get(1).error());
    }

    @Test
    void unlimitedClassAcceptsAllRows() {
        XlsxImportResult result = service.preview(workbook(List.of(
            row("K001", "CB", "S001", "", "", "", ""),
            row("K001", "CB", "S002", "", "", "", ""),
            row("K001", "CB", "S003", "", "", "", ""))));

        assertEquals(0, result.errorCount().intValue(), result.rows().get(2).error());
    }

    @Test
    void numericStudentNoCellIsReadBackAsPlainDigits() {
        XlsxImportResult result = service.preview(workbook(
            List.of(row("K001", "CB", "10001", "", "", "", ""))));
        // 10001 不在学员档案里，这里只验证数值单元格被还原成 "10001" 而不是科学计数法
        assertTrue(result.rows().get(0).error().contains("未找到学号 10001"),
            result.rows().get(0).error());

        when(studentMapper.selectList(any())).thenReturn(List.of(student(50L, "10001", "赵六")));
        XlsxImportResult ok = service.preview(workbook(
            List.of(row("K001", "CB", "10001", "赵六", "", "", "")), 2));
        assertTrue(ok.rows().get(0).valid(), ok.rows().get(0).error());
    }

    // ------------------------------------------------------------------ 落库

    @Test
    void commitCreatesRosterThroughTheSingleCreatePath() {
        XlsxImportResult result = service.commit(workbook(List.of(
            row("K001", "CB", "S001", "张三", "转介绍", "2026-03-05", "备注"))));

        assertEquals(1, result.createdCount().intValue());

        ArgumentCaptor<ClassStudentCreateRequest> created =
            ArgumentCaptor.forClass(ClassStudentCreateRequest.class);
        verify(classStudentService, times(1)).createClassStudent(created.capture());
        ClassStudentCreateRequest request = created.getValue();
        assertEquals(INSTITUTION_ID, request.institutionId().longValue());
        assertEquals(CLASS_B_ID, request.classId().longValue());
        assertEquals(STUDENT_ZHANG_ID, request.studentId().longValue());
        assertEquals("referral", request.channel());
        assertEquals(LocalDate.of(2026, 3, 5), request.joinedAt());
    }

    @Test
    void commitSkipsExistingRoster() {
        when(classStudentMapper.selectList(any())).thenReturn(List.of(
            roster(CLASS_B_ID, STUDENT_ZHANG_ID, "active")));

        XlsxImportResult result = service.commit(workbook(List.of(
            row("K001", "CB", "S001", "", "", "", ""),
            row("K001", "CB", "S003", "", "", "", ""))));

        assertEquals(1, result.createdCount().intValue());
        assertEquals(1, result.skippedCount().intValue());
        verify(classStudentService, times(1)).createClassStudent(any());
    }

    @Test
    void commitRejectsWholeBatchWhenAnyRowInvalid() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.commit(workbook(List.of(
                row("K001", "CB", "S001", "", "", "", ""),
                row("K001", "CB", "S999", "", "", "", "")))));

        assertTrue(exception.getMessage().contains("整批未导入"), exception.getMessage());
        verify(classStudentService, never()).createClassStudent(any());
    }

    // ------------------------------------------------------------------ 模板与登录态

    @Test
    void emptyTemplateIsRejectedInsteadOfImportingNothing() {
        MockMultipartFile template = new MockMultipartFile("file", "分班批量导入模板.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", service.buildTemplate());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(template));

        assertTrue(exception.getMessage().contains("未读到有效数据行"), exception.getMessage());
    }

    @Test
    void templateFirstSheetMustBeTheDataSheet() throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(service.buildTemplate()))) {
            Row header = book.getSheetAt(0).getRow(0);
            for (int index = 0; index < TITLES.length; index++) {
                Cell cell = header.getCell(index);
                assertEquals(TITLES[index], cell == null ? "" : cell.toString().trim());
            }
        }
    }

    @Test
    void templateGuideExplainsChannelValuesFromDict() throws IOException {
        StringBuilder guide = new StringBuilder();
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(service.buildTemplate()))) {
            Sheet sheet = book.getSheet("填写说明");
            for (int index = 0; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row != null && row.getCell(0) != null) {
                    guide.append(row.getCell(0).toString()).append('\n');
                }
            }
        }
        assertTrue(guide.toString().contains("front_desk/consultant/online/referral"), guide.toString());
    }

    @Test
    void accountWithoutInstitutionCannotImport() {
        authenticate(null);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.preview(workbook(List.of(
                row("K001", "CB", "S001", "", "", "", "")))));

        assertTrue(exception.getMessage().contains("未绑定机构"), exception.getMessage());
    }

    // ------------------------------------------------------------------ 造文件与造数据

    private List<Object> row(String classCode, String campusCode, String studentNo, String studentName,
                             String channel, String joinedAt, String remark) {
        return List.of(classCode, campusCode, studentNo, studentName, channel, joinedAt, remark);
    }

    private MockMultipartFile workbook(List<List<Object>> rows) {
        return workbook(rows, null);
    }

    private MockMultipartFile workbook(List<List<Object>> rows, Integer numericColumn) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("分班导入");
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
            return new MockMultipartFile("file", "分班导入.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void authenticate() {
        authenticate(INSTITUTION_ID);
    }

    private void authenticate(Long orgId) {
        AuthenticatedUser user = new AuthenticatedUser(1L, orgId, "admin",
            Set.of("EDU"), Set.of("education:class:write"), false, null);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", List.of()));
    }

    private CampusEntity campus(Long id, String campusCode) {
        CampusEntity campus = new CampusEntity();
        campus.setId(id);
        campus.setInstitutionId(INSTITUTION_ID);
        campus.setCampusCode(campusCode);
        return campus;
    }

    private TeachingClassEntity teachingClass(Long id, Long campusId, String classCode,
                                              String className, int capacity) {
        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(id);
        teachingClass.setInstitutionId(INSTITUTION_ID);
        teachingClass.setCampusId(campusId);
        teachingClass.setClassCode(classCode);
        teachingClass.setClassName(className);
        teachingClass.setCapacityLimit(capacity);
        return teachingClass;
    }

    private StudentEntity student(Long id, String studentNo, String studentName) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setInstitutionId(INSTITUTION_ID);
        student.setStudentNo(studentNo);
        student.setStudentName(studentName);
        return student;
    }

    private ClassStudentEntity roster(Long classId, Long studentId, String status) {
        ClassStudentEntity relation = new ClassStudentEntity();
        relation.setId(1L);
        relation.setInstitutionId(INSTITUTION_ID);
        relation.setClassId(classId);
        relation.setStudentId(studentId);
        relation.setStatus(status);
        return relation;
    }
}
