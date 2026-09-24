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
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.dto.TeacherCreateRequest;
import com.cornerstone.module.education.dto.TeacherImportResult;
import com.cornerstone.module.education.dto.TeacherImportRowView;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.service.TeacherService;
import com.cornerstone.module.education.service.impl.TeacherImportServiceImpl;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 教师档案批量导入：用真实 xlsx 走一遍解析、校验与落库。
 *
 * <p>刻意用 POI 造文件而不是直接喂 DTO——列顺序、数值单元格、模板首表这三处
 * 是客户手填最容易坏的地方，只有在真文件上才验得出来。</p>
 */
class TeacherImportServiceImplTest {

    private static final long INSTITUTION_ID = 1L;
    private static final long CAMPUS_ID = 20L;
    /** 模板列顺序：编码、姓名、手机号、校区编码、性别、职称、用工类型、入职日期、状态、备注 */
    private static final String[] TITLES = {
        "教师编码*", "教师姓名*", "手机号", "校区编码", "性别", "职称",
        "用工类型", "入职日期", "状态", "备注"
    };
    private static final Map<String, List<String>> DICT = Map.of(
        "gender", List.of("male", "female", "unknown"),
        "employment_type", List.of("full_time", "part_time", "contract"));

    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);
    private final CampusMapper campusMapper = mock(CampusMapper.class);
    private final TeacherService teacherService = mock(TeacherService.class);
    private final DictValueValidator dictValueValidator = mock(DictValueValidator.class);
    private final SystemDictService systemDictService = mock(SystemDictService.class);

    private final AtomicLong nextTeacherId = new AtomicLong(100L);

    private TeacherImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TeacherImportServiceImpl(teacherMapper, campusMapper, teacherService,
            dictValueValidator, systemDictService);
        authenticate();
        when(teacherMapper.selectOccupations(INSTITUTION_ID)).thenReturn(List.of());
        when(campusMapper.selectList(any())).thenReturn(List.of(campus(CAMPUS_ID, "C001")));
        // 中文条目名 → 规范值：导出文件里的性别/用工类型是中文，改完回灌必须认
        when(dictValueValidator.valueByLabel("gender"))
            .thenReturn(Map.of("男", "male", "女", "female", "未知", "unknown"));
        when(dictValueValidator.valueByLabel("employment_type"))
            .thenReturn(Map.of("全职", "full_time", "兼职", "part_time", "外聘", "contract"));
        // 字典校验按固定允许值模拟真实行为，避免测试跟着字典条目漂移
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
        when(teacherService.createTeacher(any())).thenAnswer(invocation ->
            teacher(nextTeacherId.getAndIncrement(), invocation.getArgument(0)));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 校验

    @Test
    void validRowsPassPreviewWithoutWriting() {
        TeacherImportResult result = service.preview(workbook(List.of(
            row("T001", "张老师", "13800000001", "C001", "male", "中级", "full_time", "2026-03-05", "", ""),
            row("T002", "李老师", "", "", "", "", "part_time", "2026/3/5", "active", "兼职"))));

        assertEquals(2, result.total().intValue());
        assertEquals(2, result.validCount().intValue(), result.rows().get(1).error());
        assertEquals(0, result.errorCount().intValue());
        verify(teacherService, never()).createTeacher(any());
    }

    @Test
    void missingCodeAndNameAreRejected() {
        // 只填手机号：整行全空会被当成空行跳过，到不了必填校验
        TeacherImportRowView view = service.preview(workbook(List.of(
            row("", "", "13800000001", "", "", "", "", "", "", "")))).rows().get(0);

        assertFalse(view.valid());
        assertTrue(view.error().contains("教师编码不能为空"), view.error());
        assertTrue(view.error().contains("教师姓名不能为空"), view.error());
    }

    @Test
    void blankPhoneWarnsThatTheAccountCannotBeMatched() {
        // 手机号是后续「匹配账号」唯一的抓手，缺了要当场说清楚，而不是等绑不上时才发现
        TeacherImportRowView view = service.preview(workbook(List.of(
            row("T001", "张老师", "", "", "", "", "", "", "", "")))).rows().get(0);

        assertTrue(view.valid(), view.error());
        assertTrue(view.notice().contains("无法按手机号自动匹配"), view.notice());
    }

    @Test
    void existingCodeInOwnInstitutionIsSkippedNotOverwritten() {
        when(teacherMapper.selectOccupations(INSTITUTION_ID)).thenReturn(List.of(
            teacher(77L, "T001", "13800000001")));

        TeacherImportRowView view = service.preview(workbook(List.of(
            row("T001", "张老师", "13800000001", "", "", "", "", "", "", "")))).rows().get(0);

        assertTrue(view.valid(), view.error());
        assertTrue(view.notice().contains("跳过新建"), view.notice());
        assertEquals(77L, view.teacherId().longValue());
    }

    @Test
    void phoneHeldByAnotherTeacherNamesTheOwner() {
        when(teacherMapper.selectOccupations(INSTITUTION_ID)).thenReturn(List.of(
            teacher(77L, "T001", "13800000001")));

        TeacherImportRowView view = service.preview(workbook(List.of(
            row("T002", "李老师", "13800000001", "", "", "", "", "", "", "")))).rows().get(0);

        // 撞的是 uk_jp_teacher_phone，落库必然 1062，只能报错；把占用方编码带出来省一次翻表
        assertTrue(view.error().contains("T001"), view.error());
    }

    /**
     * 编码/手机号的唯一键都不含 deleted，软删档案照样占着这两个值。
     * 预览看不见它们就是"整批校验全绿、提交时 1062 整批回滚"，所以占用方必须在这一轮就报出来。
     */
    @Test
    void codeHeldBySoftDeletedArchiveIsRejectedInPreview() {
        when(teacherMapper.selectOccupations(INSTITUTION_ID)).thenReturn(List.of(
            retiredTeacher(77L, "T001", "13800000001")));

        TeacherImportRowView view = service.preview(workbook(List.of(
            row("T001", "张老师", "13800000009", "", "", "", "", "", "", "")))).rows().get(0);

        assertFalse(view.valid());
        assertTrue(view.error().contains("已删除的历史档案"), view.error());
    }

    @Test
    void phoneHeldBySoftDeletedArchiveIsRejectedInPreview() {
        when(teacherMapper.selectOccupations(INSTITUTION_ID)).thenReturn(List.of(
            retiredTeacher(77L, "T001", "13800000001")));

        TeacherImportRowView view = service.preview(workbook(List.of(
            row("T002", "李老师", "13800000001", "", "", "", "", "", "", "")))).rows().get(0);

        assertFalse(view.valid());
        assertTrue(view.error().contains("已删除的历史档案"), view.error());
    }

    @Test
    void duplicateCodeWithinOneFileIsRejected() {
        TeacherImportResult result = service.preview(workbook(List.of(
            row("T001", "张老师", "13800000001", "", "", "", "", "", "", ""),
            row("T001", "李老师", "13800000002", "", "", "", "", "", "", ""))));

        assertEquals(1, result.errorCount().intValue());
        assertTrue(result.rows().get(1).error().contains("出现多行"), result.rows().get(1).error());
    }

    @Test
    void unknownCampusCodeIsRejectedAndAvailableCodesListed() {
        TeacherImportRowView view = service.preview(workbook(List.of(
            row("T001", "张老师", "", "C999", "", "", "", "", "", "")))).rows().get(0);

        assertTrue(view.error().contains("未找到校区编码 C999"), view.error());
        assertTrue(view.error().contains("C001"), "报错要给出可用编码：" + view.error());
    }

    @Test
    void invalidDictValueBecomesRowErrorNotBatchFailure() {
        // 一份文件里错一个性别不能让其余两百行白填，所以字典异常要收成行级错误
        TeacherImportRowView view = service.preview(workbook(List.of(
            row("T001", "张老师", "", "", "MALE", "", "", "", "", "")))).rows().get(0);

        assertFalse(view.valid());
        assertTrue(view.error().contains("性别取值无效"), view.error());
        assertTrue(view.error().contains("male"), view.error());
    }

    /** 导出 → 改 → 回灌是本特性的基本用法：中文条目名归一成 item_value 后再走校验和落库 */
    @Test
    void chineseDictNamesAreNormalizedToItemValues() {
        ArgumentCaptor<TeacherCreateRequest> created = ArgumentCaptor.forClass(TeacherCreateRequest.class);

        service.commit(workbook(List.of(
            row("T001", "张老师", "13800000001", "", "女", "中级", "兼职", "", "", ""))));

        verify(teacherService).createTeacher(created.capture());
        assertEquals("female", created.getValue().gender());
        assertEquals("part_time", created.getValue().employmentType());
    }

    @Test
    void unknownChineseDictNameStillFailsWithTheCanonicalValueList() {
        // 反查表里没有的写法不在导入阶段瞎猜，交给校验报错并把规范值列出来
        TeacherImportRowView view = service.preview(workbook(List.of(
            row("T001", "张老师", "", "", "男生", "", "", "", "", "")))).rows().get(0);

        assertFalse(view.valid());
        assertTrue(view.error().contains("性别取值无效"), view.error());
    }

    @Test
    void malformedAndFutureHiredAtAreRejected() {
        TeacherImportResult result = service.preview(workbook(List.of(
            row("T001", "张老师", "", "", "", "", "", "20260305A", "", ""),
            row("T002", "李老师", "", "", "", "", "", LocalDate.now().plusDays(1).toString(), "", ""))));

        assertTrue(result.rows().get(0).error().contains("入职日期格式不对"), result.rows().get(0).error());
        assertTrue(result.rows().get(1).error().contains("不能晚于当前日期"), result.rows().get(1).error());
    }

    @Test
    void blankStatusAndEmploymentEchoTheirDefaults() {
        TeacherImportRowView view = service.preview(workbook(List.of(
            row("T001", "张老师", "", "", "", "", "", "", "", "")))).rows().get(0);

        assertEquals("active", view.status());
        assertEquals("full_time", view.employmentType());
    }

    @Test
    void invalidStatusIsRejected() {
        TeacherImportRowView view = service.preview(workbook(List.of(
            row("T001", "张老师", "", "", "", "", "", "", "resigned", "")))).rows().get(0);

        assertTrue(view.error().contains("状态只能填"), view.error());
    }

    @Test
    void numericPhoneCellIsReadBackAsPlainDigits() {
        // 客户直接敲数字，Excel 存成数值单元格，读回来不能是科学计数法
        TeacherImportResult result = service.preview(workbook(
            List.of(row("T001", "张老师", "13800000001", "", "", "", "", "", "", "")), 2));

        assertEquals("13800000001", result.rows().get(0).phone(), result.rows().get(0).phone());
        assertEquals(0, result.errorCount().intValue(), result.rows().get(0).error());
    }

    // ------------------------------------------------------------------ 落库

    @Test
    void commitCreatesTeachersThroughTheSingleCreatePath() {
        TeacherImportResult result = service.commit(workbook(List.of(
            row("T001", "张老师", "13800000001", "C001", "male", "中级", "full_time", "2026-03-05", "", "备注"))));

        assertEquals(1, result.createdCount().intValue());
        assertNotNull(result.rows().get(0).teacherId());

        ArgumentCaptor<TeacherCreateRequest> created = ArgumentCaptor.forClass(TeacherCreateRequest.class);
        verify(teacherService, times(1)).createTeacher(created.capture());
        TeacherCreateRequest request = created.getValue();
        // 机构来自登录态而不是文件：模板里根本没有这一列
        assertEquals(INSTITUTION_ID, request.institutionId().longValue());
        // 校区编码在预览阶段就解析成 ID，落库继续走 createTeacher 的归属校验
        assertEquals(CAMPUS_ID, request.campusId().longValue());
        assertEquals(LocalDate.of(2026, 3, 5), request.hiredAt());
        assertEquals("active", request.status());
    }

    @Test
    void commitSkipsExistingCode() {
        when(teacherMapper.selectOccupations(INSTITUTION_ID)).thenReturn(List.of(
            teacher(77L, "T001", "13800000001"), teacher(78L, "T002", "13800000002")));

        TeacherImportResult result = service.commit(workbook(List.of(
            row("T001", "张老师", "13800000001", "", "", "", "", "", "", ""),
            row("T003", "王老师", "13800000003", "", "", "", "", "", "", ""))));

        assertEquals(1, result.createdCount().intValue());
        assertEquals(1, result.skippedCount().intValue());
        verify(teacherService, times(1)).createTeacher(any());
    }

    @Test
    void commitRejectsWholeBatchWhenAnyRowInvalid() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.commit(workbook(List.of(
                row("T001", "张老师", "", "", "", "", "", "", "", ""),
                row("", "", "13800000002", "", "", "", "", "", "", "")))));

        assertTrue(exception.getMessage().contains("整批未导入"), exception.getMessage());
        verify(teacherService, never()).createTeacher(any());
    }

    // ------------------------------------------------------------------ 模板与登录态

    @Test
    void emptyTemplateIsRejectedInsteadOfImportingNothing() {
        // 模板首表只有表头，原样传回来必须给出"没数据"而不是静默成功
        MockMultipartFile template = new MockMultipartFile("file", "教师档案批量导入模板.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", service.buildTemplate());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(template));

        assertTrue(exception.getMessage().contains("未读到有效数据行"), exception.getMessage());
    }

    @Test
    void templateFirstSheetMustBeTheDataSheet() throws IOException {
        // 说明页排在前面的话，客户照着填就永远导不进去
        List<List<String>> dataRows = readDataSheet(service.buildTemplate());

        assertEquals(TITLES.length, dataRows.get(0).size());
        for (int index = 0; index < TITLES.length; index++) {
            assertEquals(TITLES[index], dataRows.get(0).get(index));
        }
    }

    @Test
    void templateDropdownsComeFromTheDictNotFromHardcodedValues() throws IOException {
        // 字典条目可被运维扩充，模板里的下拉候选必须跟着字典走
        String guide = readGuideSheet(service.buildTemplate());

        assertTrue(guide.contains("male/female/unknown"), guide);
        assertTrue(guide.contains("full_time/part_time/contract"), guide);
    }

    @Test
    void nonXlsxFileIsRejected() {
        MockMultipartFile csv = new MockMultipartFile("file", "教师.csv", "text/csv",
            "T001,张老师".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(csv));

        assertTrue(exception.getMessage().contains("xlsx"), exception.getMessage());
    }

    @Test
    void accountWithoutInstitutionCannotImport() {
        authenticate(null);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.preview(workbook(List.of(
                row("T001", "张老师", "", "", "", "", "", "", "", "")))));

        assertTrue(exception.getMessage().contains("未绑定机构"), exception.getMessage());
    }

    // ------------------------------------------------------------------ 造文件与造数据

    private List<Object> row(String teacherCode, String teacherName, String phone, String campusCode,
                             String gender, String title, String employmentType, String hiredAt,
                             String status, String remark) {
        return List.of(teacherCode, teacherName, phone, campusCode, gender, title, employmentType,
            hiredAt, status, remark);
    }

    private MockMultipartFile workbook(List<List<Object>> rows) {
        return workbook(rows, null);
    }

    /** numericColumn 非空时把该列按数值单元格写入，模拟客户直接敲数字后 Excel 的存法 */
    private MockMultipartFile workbook(List<List<Object>> rows, Integer numericColumn) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("教师档案导入");
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
            return new MockMultipartFile("file", "教师档案导入.xlsx",
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
            Set.of("EDU"), Set.of("education:write"), false, null);
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

    private TeacherEntity teacher(Long id, String teacherCode, String phone) {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(id);
        teacher.setInstitutionId(INSTITUTION_ID);
        teacher.setTeacherCode(teacherCode);
        teacher.setPhone(phone);
        return teacher;
    }

    /** 软删档案：页面上看不见，但编码与手机号仍被唯一键占着 */
    private TeacherEntity retiredTeacher(Long id, String teacherCode, String phone) {
        TeacherEntity teacher = teacher(id, teacherCode, phone);
        teacher.setDeleted(Boolean.TRUE);
        return teacher;
    }

    private TeacherEntity teacher(Long id, TeacherCreateRequest request) {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(id);
        teacher.setInstitutionId(request.institutionId());
        teacher.setCampusId(request.campusId());
        teacher.setTeacherCode(request.teacherCode());
        teacher.setTeacherName(request.teacherName());
        teacher.setPhone(request.phone());
        return teacher;
    }
}
