package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.cornerstone.module.education.domain.entity.ClassTypeEntity;
import com.cornerstone.module.education.domain.entity.CourseEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.TeachingClassCreateRequest;
import com.cornerstone.module.education.dto.XlsxImportResult;
import com.cornerstone.module.education.dto.XlsxImportRowView;
import com.cornerstone.module.education.mapper.ClassTypeMapper;
import com.cornerstone.module.education.mapper.CourseMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.TeachingClassService;
import com.cornerstone.module.education.service.impl.ClassImportServiceImpl;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.TermMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
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
 * 班级档案批量导入：重点在"唯一键按校区"（编码、名称都是 校区ID + 业务键），
 * 以及各种引用编码（校区/学期/课程/班型/教师）的解析与报错。
 */
class ClassImportServiceImplTest {

    private static final long INSTITUTION_ID = 1L;
    private static final long CAMPUS_A_ID = 20L;
    private static final long CAMPUS_B_ID = 21L;
    /** 模板列顺序：编码、名称、校区、学期、课程、班型、教师、容量、周节数、开班日期、状态、备注 */
    private static final String[] TITLES = {
        "班级编码", "班级名称*", "校区编码*", "学期编码", "课程编码", "班型编码",
        "主讲教师编码", "容量上限", "每周应上节数", "开班日期", "状态", "备注"
    };

    private final TeachingClassMapper teachingClassMapper = mock(TeachingClassMapper.class);
    private final CampusMapper campusMapper = mock(CampusMapper.class);
    private final TermMapper termMapper = mock(TermMapper.class);
    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final ClassTypeMapper classTypeMapper = mock(ClassTypeMapper.class);
    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);
    private final TeachingClassService teachingClassService = mock(TeachingClassService.class);

    private final AtomicLong nextClassId = new AtomicLong(100L);

    private ClassImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClassImportServiceImpl(teachingClassMapper, campusMapper, termMapper,
            courseMapper, classTypeMapper, teacherMapper, teachingClassService);
        authenticate();
        when(teachingClassMapper.selectList(any())).thenReturn(List.of());
        when(campusMapper.selectList(any())).thenReturn(List.of(
            campus(CAMPUS_A_ID, "C001"), campus(CAMPUS_B_ID, "C002")));
        when(termMapper.selectList(any())).thenReturn(List.of(term(30L, "T1")));
        when(courseMapper.selectList(any())).thenReturn(List.of(course(40L, "CO1")));
        when(classTypeMapper.selectList(any())).thenReturn(List.of(classType(50L, "CT1")));
        when(teacherMapper.selectList(any())).thenReturn(List.of(teacher(60L, "TCH1")));
        when(teachingClassService.createClass(any())).thenAnswer(invocation -> {
            TeachingClassCreateRequest request = invocation.getArgument(0);
            TeachingClassEntity entity = new TeachingClassEntity();
            entity.setId(nextClassId.getAndIncrement());
            entity.setInstitutionId(request.institutionId());
            entity.setCampusId(request.campusId());
            entity.setClassCode(request.classCode() == null ? "CLS-auto-1" : request.classCode());
            entity.setClassName(request.className());
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
            row("K001", "一班", "C001", "T1", "CO1", "CT1", "TCH1", "30", "4", "2026-09-01", "active", ""),
            row("", "二班", "C002", "", "", "", "", "", "", "", "", "备注"))));

        assertEquals(2, result.validCount().intValue(), result.rows().get(0).error());
        assertEquals(0, result.errorCount().intValue());
        verify(teachingClassService, never()).createClass(any());
    }

    @Test
    void missingNameAndCampusAreRejected() {
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("K001", "", "", "", "", "", "", "", "", "", "", "")))).rows().get(0);

        assertFalse(view.valid());
        assertTrue(view.error().contains("班级名称不能为空"), view.error());
        assertTrue(view.error().contains("校区编码不能为空"), view.error());
    }

    @Test
    void unknownCampusCodeIsRejectedAndAvailableCodesListed() {
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("K001", "一班", "C999", "", "", "", "", "", "", "", "", "")))).rows().get(0);

        assertTrue(view.error().contains("未找到校区编码 C999"), view.error());
        assertTrue(view.error().contains("C001"), "报错要给出可用编码：" + view.error());
    }

    @Test
    void existingCodeInSameCampusIsSkippedNotOverwritten() {
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(
            existingClass(77L, CAMPUS_A_ID, "K001", "一班")));

        XlsxImportRowView view = service.preview(workbook(List.of(
            row("K001", "一班", "C001", "", "", "", "", "", "", "", "", "")))).rows().get(0);

        assertTrue(view.valid(), view.error());
        assertTrue(view.notice().contains("跳过新建"), view.notice());
    }

    /**
     * 班级名称的唯一键是「校区ID + 名称」：同校区重名是硬冲突（不能像同编码那样跳过），
     * 但换到另一个校区就可以同名。
     */
    @Test
    void nameConflictIsScopedToCampus() {
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(
            existingClass(77L, CAMPUS_A_ID, "K001", "一班")));

        XlsxImportResult result = service.preview(workbook(List.of(
            row("K002", "一班", "C001", "", "", "", "", "", "", "", "", ""),
            row("K003", "一班", "C002", "", "", "", "", "", "", "", "", ""))));

        assertFalse(result.rows().get(0).valid());
        assertTrue(result.rows().get(0).error().contains("已被班级编码 K001 使用"),
            result.rows().get(0).error());
        assertTrue(result.rows().get(1).valid(), "另一个校区同名班应当允许：" + result.rows().get(1).error());
    }

    @Test
    void duplicateCodeWithinOneFileIsRejected() {
        XlsxImportResult result = service.preview(workbook(List.of(
            row("K001", "一班", "C001", "", "", "", "", "", "", "", "", ""),
            row("K001", "二班", "C001", "", "", "", "", "", "", "", "", ""))));

        assertEquals(1, result.errorCount().intValue());
        assertTrue(result.rows().get(1).error().contains("出现多行"), result.rows().get(1).error());
    }

    @Test
    void unknownReferenceCodesAreRejected() {
        XlsxImportResult result = service.preview(workbook(List.of(
            row("K001", "一班", "C001", "T999", "CO999", "CT999", "TCH999", "", "", "", "", ""))));

        String error = result.rows().get(0).error();
        assertTrue(error.contains("未找到学期编码 T999"), error);
        assertTrue(error.contains("未找到课程编码 CO999"), error);
        assertTrue(error.contains("未找到班型编码 CT999"), error);
        assertTrue(error.contains("未找到主讲教师编码 TCH999"), error);
    }

    @Test
    void invalidStatusIsRejectedAndBlankFallsBackToPlanning() {
        XlsxImportResult result = service.preview(workbook(List.of(
            row("K001", "一班", "C001", "", "", "", "", "", "", "", "resigned", ""),
            row("K002", "二班", "C001", "", "", "", "", "", "", "", "", ""))));

        assertTrue(result.rows().get(0).error().contains("状态取值无效"), result.rows().get(0).error());
        assertTrue(result.rows().get(0).error().contains("planning"), result.rows().get(0).error());
        assertEquals("planning", result.rows().get(1).cells().get("status"));
    }

    @Test
    void futureOpenedAtIsAllowed() {
        // 规划中的班本来就可以填未来的开班日期，不能照搬入职/入学日期的"不能晚于今天"
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("K001", "一班", "C001", "", "", "", "", "", "",
                LocalDate.now().plusMonths(2).toString(), "", "")))).rows().get(0);

        assertTrue(view.valid(), view.error());
    }

    @Test
    void malformedOpenedAtAndNumbersAreRejected() {
        XlsxImportResult result = service.preview(workbook(List.of(
            row("K001", "一班", "C001", "", "", "", "", "-1", "0", "20260901X", "", ""))));

        String error = result.rows().get(0).error();
        assertTrue(error.contains("容量上限必须大于或等于 0"), error);
        assertTrue(error.contains("每周应上节数必须大于或等于 1"), error);
        assertTrue(error.contains("开班日期格式不对"), error);
    }

    @Test
    void numericZeroCapacityIsAccepted() {
        XlsxImportRowView view = service.preview(workbook(List.of(
            row("K001", "一班", "C001", "", "", "", "", "0", "", "", "", "")))).rows().get(0);

        assertTrue(view.valid(), view.error());
    }

    // ------------------------------------------------------------------ 落库

    @Test
    void commitCreatesClassThroughTheSingleCreatePath() {
        XlsxImportResult result = service.commit(workbook(List.of(
            row("K001", "一班", "C001", "T1", "CO1", "CT1", "TCH1", "30", "4",
                "2026-09-01", "active", "备注"))));

        assertEquals(1, result.createdCount().intValue());

        ArgumentCaptor<TeachingClassCreateRequest> created =
            ArgumentCaptor.forClass(TeachingClassCreateRequest.class);
        verify(teachingClassService, times(1)).createClass(created.capture());
        TeachingClassCreateRequest request = created.getValue();
        assertEquals(INSTITUTION_ID, request.institutionId().longValue());
        // 编码在预览阶段就解析成 ID，落库继续走 createClass 的归属校验
        assertEquals(CAMPUS_A_ID, request.campusId().longValue());
        assertEquals(30L, request.termId().longValue());
        assertEquals(40L, request.courseId().longValue());
        assertEquals(50L, request.classTypeId().longValue());
        assertEquals(60L, request.teacherId().longValue());
        assertEquals(30, request.capacityLimit().intValue());
        assertEquals(4, request.weeklyLessons().intValue());
        assertEquals("weekly", request.weeklyLessonsMode());
        assertEquals(LocalDate.of(2026, 9, 1), request.openedAt());
    }

    @Test
    void blankClassCodeIsGeneratedAndBackFilledInResult() {
        XlsxImportResult result = service.commit(workbook(List.of(
            row("", "一班", "C001", "", "", "", "", "", "", "", "", ""))));

        assertEquals("CLS-auto-1", result.rows().get(0).cells().get("classCode"));

        ArgumentCaptor<TeachingClassCreateRequest> created =
            ArgumentCaptor.forClass(TeachingClassCreateRequest.class);
        verify(teachingClassService).createClass(created.capture());
        assertNull(created.getValue().classCode());
    }

    @Test
    void commitSkipsExistingCode() {
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(
            existingClass(77L, CAMPUS_A_ID, "K001", "一班")));

        XlsxImportResult result = service.commit(workbook(List.of(
            row("K001", "一班", "C001", "", "", "", "", "", "", "", "", ""),
            row("K002", "二班", "C001", "", "", "", "", "", "", "", "", ""))));

        assertEquals(1, result.createdCount().intValue());
        assertEquals(1, result.skippedCount().intValue());
        verify(teachingClassService, times(1)).createClass(any());
    }

    @Test
    void commitRejectsWholeBatchWhenAnyRowInvalid() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.commit(workbook(List.of(
                row("K001", "一班", "C001", "", "", "", "", "", "", "", "", ""),
                row("K002", "", "C001", "", "", "", "", "", "", "", "", "")))));

        assertTrue(exception.getMessage().contains("整批未导入"), exception.getMessage());
        verify(teachingClassService, never()).createClass(any());
    }

    // ------------------------------------------------------------------ 模板与登录态

    @Test
    void emptyTemplateIsRejectedInsteadOfImportingNothing() {
        MockMultipartFile template = new MockMultipartFile("file", "班级档案批量导入模板.xlsx",
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
    void templateGuideExplainsStatusCodeValues() throws IOException {
        // 班级状态是代码判定值，模板不翻译，必须在说明页用中文解释，否则操作员看不懂 planning
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
        assertTrue(guide.toString().contains("planning / active / inactive / closed"), guide.toString());
        assertTrue(guide.toString().contains("规划中"), guide.toString());
    }

    @Test
    void accountWithoutInstitutionCannotImport() {
        authenticate(null);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.preview(workbook(List.of(
                row("K001", "一班", "C001", "", "", "", "", "", "", "", "", "")))));

        assertTrue(exception.getMessage().contains("未绑定机构"), exception.getMessage());
    }

    // ------------------------------------------------------------------ 造文件与造数据

    private List<Object> row(String classCode, String className, String campusCode, String termCode,
                             String courseCode, String classTypeCode, String teacherCode,
                             String capacity, String weeklyLessons, String openedAt, String status,
                             String remark) {
        return List.of(classCode, className, campusCode, termCode, courseCode, classTypeCode,
            teacherCode, capacity, weeklyLessons, openedAt, status, remark);
    }

    private MockMultipartFile workbook(List<List<Object>> rows) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("班级档案导入");
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
            }
            book.write(out);
            return new MockMultipartFile("file", "班级档案导入.xlsx",
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

    private TermEntity term(Long id, String termCode) {
        TermEntity term = new TermEntity();
        term.setId(id);
        term.setInstitutionId(INSTITUTION_ID);
        term.setTermCode(termCode);
        return term;
    }

    private CourseEntity course(Long id, String courseCode) {
        CourseEntity course = new CourseEntity();
        course.setId(id);
        course.setInstitutionId(INSTITUTION_ID);
        course.setCourseCode(courseCode);
        return course;
    }

    private ClassTypeEntity classType(Long id, String typeCode) {
        ClassTypeEntity classType = new ClassTypeEntity();
        classType.setId(id);
        classType.setInstitutionId(INSTITUTION_ID);
        classType.setTypeCode(typeCode);
        return classType;
    }

    private TeacherEntity teacher(Long id, String teacherCode) {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(id);
        teacher.setInstitutionId(INSTITUTION_ID);
        teacher.setTeacherCode(teacherCode);
        return teacher;
    }

    private TeachingClassEntity existingClass(Long id, Long campusId, String classCode, String className) {
        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(id);
        teachingClass.setInstitutionId(INSTITUTION_ID);
        teachingClass.setCampusId(campusId);
        teachingClass.setClassCode(classCode);
        teachingClass.setClassName(className);
        return teachingClass;
    }
}
