package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.dto.TeacherPageQuery;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.service.impl.TeacherServiceImpl;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 教师档案导出：把生成的 xlsx 读回来核对列位与取值。
 *
 * <p>导出文件是导入模板的回执，列顺序错了客户改完再导回去就全乱，所以不看 DTO 内容而看文件。</p>
 */
class TeacherServiceImplExportTest {

    private static final long INSTITUTION_ID = 1L;
    private static final long CAMPUS_ID = 20L;
    private static final long USER_ID = 30L;
    private static final String[] HEADERS = {
        "教师编码", "教师姓名", "手机号", "校区编码", "性别", "职称",
        "用工类型", "入职日期", "状态", "备注", "关联账号"
    };

    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);
    private final InstitutionMapper institutionMapper = mock(InstitutionMapper.class);
    private final CampusMapper campusMapper = mock(CampusMapper.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final DictValueValidator dictValueValidator = mock(DictValueValidator.class);

    private TeacherServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TeacherServiceImpl(teacherMapper, institutionMapper, campusMapper,
            userAccountMapper, dictValueValidator);
        when(institutionMapper.selectById(INSTITUTION_ID)).thenReturn(new InstitutionEntity());
        when(campusMapper.selectBatchIds(any())).thenReturn(List.of(campus()));
        when(userAccountMapper.selectBatchIds(any())).thenReturn(List.of(account()));
        // 字典列导出给人看，值必须翻成 item_name，所以按真实条目把两张表喂给校验器
        when(dictValueValidator.labels("gender"))
            .thenReturn(Map.of("male", "男", "female", "女", "unknown", "未知"));
        when(dictValueValidator.labels("employment_type"))
            .thenReturn(Map.of("full_time", "全职", "part_time", "兼职", "contract", "外聘"));
        when(dictValueValidator.labels("teacher_status"))
            .thenReturn(Map.of("active", "在职", "suspended", "暂停", "left", "离职"));
    }

    @Test
    void exportWritesCampusCodeAndLinkedUsernameInsteadOfRawIds() throws IOException {
        when(teacherMapper.selectCount(any())).thenReturn(1L);
        when(teacherMapper.selectList(any())).thenReturn(List.of(
            teacher(100L, "T001", "张老师", CAMPUS_ID, USER_ID)));

        List<List<String>> rows = readRows(service.exportTeachers(query()));

        assertEquals(2, rows.size(), "一行表头 + 一行数据");
        assertEquals(List.of(HEADERS), rows.get(0));
        assertEquals(List.of("T001", "张老师", "13800000001", "C001", "男", "中级",
            "全职", "2026-03-05", "在职", "备注一", "teacher01"), rows.get(1));
    }

    /** 字典里查不到的历史脏值照原样导出：宁可难看，也不硬映射成某个合法值（与前端回显同一口径） */
    @Test
    void exportKeepsDictValuesTheDictionaryNoLongerHas() throws IOException {
        TeacherEntity legacy = teacher(102L, "T003", "历史值", null, null);
        legacy.setGender("other");
        legacy.setEmploymentType("seasonal");
        legacy.setStatus("retired");
        when(teacherMapper.selectCount(any())).thenReturn(1L);
        when(teacherMapper.selectList(any())).thenReturn(List.of(legacy));

        List<List<String>> rows = readRows(service.exportTeachers(query()));

        assertEquals("other", rows.get(1).get(4));
        assertEquals("seasonal", rows.get(1).get(6));
        assertEquals("retired", rows.get(1).get(8));
    }

    /** 全批都没绑账号时取账号名的 Map 是空的，而档案的 campusId/userId 也可以为空——这里曾经直接 NPE */
    @Test
    void exportLeavesCampusAndAccountBlankWhenNothingIsLinked() throws IOException {
        when(teacherMapper.selectCount(any())).thenReturn(1L);
        when(teacherMapper.selectList(any())).thenReturn(List.of(bareTeacher(101L, "T002", "李老师")));

        List<List<String>> rows = readRows(service.exportTeachers(query()));

        assertEquals(List.of("T002", "李老师", "", "", "", "", "", "", "", "", ""), rows.get(1));
    }

    @Test
    void exportRejectsEmptyFilterRangeInsteadOfWritingAHeaderOnlyFile() {
        when(teacherMapper.selectCount(any())).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.exportTeachers(query()));

        assertTrue(exception.getMessage().contains("没有教师数据"), exception.getMessage());
    }

    private TeacherPageQuery query() {
        TeacherPageQuery query = new TeacherPageQuery();
        query.setInstitutionId(INSTITUTION_ID);
        return query;
    }

    private TeacherEntity teacher(Long id, String code, String name, Long campusId, Long userId) {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(id);
        teacher.setInstitutionId(INSTITUTION_ID);
        teacher.setTeacherCode(code);
        teacher.setTeacherName(name);
        teacher.setPhone("13800000001");
        teacher.setCampusId(campusId);
        teacher.setUserId(userId);
        teacher.setGender("male");
        teacher.setTitle("中级");
        teacher.setEmploymentType("full_time");
        teacher.setHiredAt(LocalDate.of(2026, 3, 5));
        teacher.setStatus("active");
        teacher.setRemark("备注一");
        return teacher;
    }

    /** 只有编码与姓名的档案：其余列全为空，导出应留空白而不是报错 */
    private TeacherEntity bareTeacher(Long id, String code, String name) {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(id);
        teacher.setInstitutionId(INSTITUTION_ID);
        teacher.setTeacherCode(code);
        teacher.setTeacherName(name);
        return teacher;
    }

    private CampusEntity campus() {
        CampusEntity campus = new CampusEntity();
        campus.setId(CAMPUS_ID);
        campus.setInstitutionId(INSTITUTION_ID);
        campus.setCampusCode("C001");
        return campus;
    }

    private UserAccountEntity account() {
        UserAccountEntity account = new UserAccountEntity();
        account.setId(USER_ID);
        account.setInstitutionId(INSTITUTION_ID);
        account.setUsername("teacher01");
        account.setStatus("active");
        return account;
    }

    private List<List<String>> readRows(byte[] xlsx) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = book.getSheetAt(0);
            assertEquals("教师档案", sheet.getSheetName());
            List<List<String>> rows = new ArrayList<>();
            for (int index = 0; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int column = 0; column < HEADERS.length; column++) {
                        Cell cell = row.getCell(column);
                        cells.add(cell == null ? "" : cell.toString().trim());
                    }
                }
                rows.add(cells);
            }
            return rows;
        }
    }
}
