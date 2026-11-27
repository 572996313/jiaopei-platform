package com.cornerstone.core.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.code.CodePattern;
import com.cornerstone.core.system.code.CodeRuleSpec;
import com.cornerstone.core.system.service.CodeRuleService;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.dto.InstitutionCreateRequest;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.core.tenant.service.impl.InstitutionServiceImpl;
import com.cornerstone.module.education.course.dto.ClassTypeCreateRequest;
import com.cornerstone.module.education.course.dto.CourseCategoryCreateRequest;
import com.cornerstone.module.education.course.dto.CourseCreateRequest;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.fee.dto.FeeItemCreateRequest;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.ClassTypeMapper;
import com.cornerstone.module.education.mapper.CourseMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.student.dto.StudentCreateRequest;
import com.cornerstone.module.education.student.service.impl.StudentServiceImpl;
import com.cornerstone.module.education.support.DictValueValidator;
import com.cornerstone.module.education.teacher.dto.TeacherCreateRequest;
import com.cornerstone.module.education.teachingclass.dto.TeachingClassCreateRequest;
import com.cornerstone.module.education.teachingclass.service.ClassChangeNoticeService;
import com.cornerstone.module.education.teachingclass.service.ClassTeacherService;
import com.cornerstone.module.education.teachingclass.service.impl.TeachingClassServiceImpl;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.dto.CampusCreateRequest;
import com.cornerstone.module.organization.dto.ClassroomCreateRequest;
import com.cornerstone.module.organization.dto.TermCreateRequest;
import com.cornerstone.module.organization.dto.TimeSlotCreateRequest;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.TermMapper;
import com.cornerstone.module.organization.service.impl.CampusServiceImpl;
import com.cornerstone.support.CodeRuleTestSupport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 编号规则接进创建路径的守护（不连库）。
 *
 * <p>取号引擎自己的行为由 {@code CodeRuleServiceImplTest} 守住，这里守的是<b>接线</b>：业务 Service
 * 有没有把 DTO 里那一列交给 {@code resolveForCreate}、有没有把返回的号存进去。口径是"填了就用他填的、
 * 没填才补号；没启用规则又没填，报的是中文的 XX编号不能为空"。这三件事在页面上都用一条空表单点下去就能
 * 触发，但走偏的方式很多（原值直接落库、补出来的号被丢掉、判空落回 Bean Validation 的英文消息），
 * 所以逐个钉住。</p>
 *
 * <p>最后一个用例守反向的坑：创建 DTO 的编号列只要还挂着 {@code @NotBlank}，空表单在进 Service 之前
 * 就被挡下，编号规则页把"启用"打开也不起作用、也不报错。这种"配了没生效"最难查，宁可在合入期就红。</p>
 */
class CodeRuleWiringTest {

    /** 资源码 -> 它的创建请求 DTO。编号列名由登记里的列名驼峰推导，所以这里只登记类。 */
    private static final Map<String, Class<?>> CREATE_DTO_BY_RESOURCE = Map.ofEntries(
        Map.entry("institution", InstitutionCreateRequest.class),
        Map.entry("campus", CampusCreateRequest.class),
        Map.entry("classroom", ClassroomCreateRequest.class),
        Map.entry("term", TermCreateRequest.class),
        Map.entry("time_slot", TimeSlotCreateRequest.class),
        Map.entry("teacher", TeacherCreateRequest.class),
        Map.entry("student", StudentCreateRequest.class),
        Map.entry("class", TeachingClassCreateRequest.class),
        Map.entry("course", CourseCreateRequest.class),
        Map.entry("course_category", CourseCategoryCreateRequest.class),
        Map.entry("class_type", ClassTypeCreateRequest.class),
        Map.entry("fee_item", FeeItemCreateRequest.class));

    private final InstitutionMapper institutionMapper = mock(InstitutionMapper.class);
    private final CampusMapper campusMapper = mock(CampusMapper.class);
    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final TeachingClassMapper teachingClassMapper = mock(TeachingClassMapper.class);
    private final SystemDictService systemDictService = mock(SystemDictService.class);
    private final DictValueValidator educationDict = new DictValueValidator(systemDictService);
    private final com.cornerstone.module.organization.service.support.DictValueValidator organizationDict =
        new com.cornerstone.module.organization.service.support.DictValueValidator(systemDictService);

    @BeforeAll
    static void initEntityTableInfo() {
        // 判重的 LambdaQueryWrapper 要按方法引用反推列名，生产由 mapper 扫描注册，单测自己补一次
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, InstitutionEntity.class);
        TableInfoHelper.initTableInfo(assistant, CampusEntity.class);
        TableInfoHelper.initTableInfo(assistant, StudentEntity.class);
        TableInfoHelper.initTableInfo(assistant, TeachingClassEntity.class);
    }

    @Test
    void 留空并且没有启用规则时报中文不能为空不落库() {
        CodeRuleService unconfigured = CodeRuleTestSupport.unconfiguredRules();

        BusinessException institutionError = assertThrows(BusinessException.class,
            () -> institution(unconfigured).createInstitution(institutionRequest(null)));
        assertEquals("机构编码不能为空", institutionError.getMessage());

        BusinessException campusError = assertThrows(BusinessException.class,
            () -> campus(unconfigured).createCampus(campusRequest("   ")));
        assertEquals("校区编码不能为空", campusError.getMessage());

        BusinessException studentError = assertThrows(BusinessException.class,
            () -> student(unconfigured).createStudent(studentRequest(null)));
        assertEquals("学号不能为空", studentError.getMessage());

        verify(institutionMapper, never()).insert(any(InstitutionEntity.class));
        verify(campusMapper, never()).insert(any(CampusEntity.class));
        verify(studentMapper, never()).insert(any(StudentEntity.class));
    }

    @Test
    void 留空并且启用了规则时补出来的号落库() {
        // 号段里库里最大的同号是 …7 / …4，所以补出来的应该是 …8 / …5
        CampusEntity savedCampus = campus(CodeRuleTestSupport.rulesEnabledFor("campus", codeOf("campus", 7)))
            .createCampus(campusRequest(null));
        assertEquals(codeOf("campus", 8), savedCampus.getCampusCode());

        StudentEntity savedStudent = student(CodeRuleTestSupport.rulesEnabledFor("student", codeOf("student", 4)))
            .createStudent(studentRequest("  "));
        assertEquals(codeOf("student", 5), savedStudent.getStudentNo());
    }

    @Test
    void 操作员自己填了编号就存他填的() {
        // 启用状态下也一样：填了就不该被换成生成的号（未启用时同一条返回路径，不必再测一遍）
        StudentEntity savedStudent = student(CodeRuleTestSupport.rulesEnabledFor("student", codeOf("student", 4)))
            .createStudent(studentRequest("  自填号  "));

        assertEquals("自填号", savedStudent.getStudentNo());
    }

    @Test
    void 班级新增留空在未启用时同样报中文空() {
        TeachingClassServiceImpl service = classService(CodeRuleTestSupport.unconfiguredRules());

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.createClass(classRequest(null)));

        assertEquals("班级编码不能为空", error.getMessage());
        verify(teachingClassMapper, never()).insert(any(TeachingClassEntity.class));
    }

    @Test
    void 班级编辑留空仍报空并且不问编号服务() {
        // 补号只在新增路径：编辑是整表单覆盖，留空不能被当成"再要一个新号"
        CodeRuleService rules = mock(CodeRuleService.class);
        TeachingClassServiceImpl service = classService(rules);
        TeachingClassEntity existing = new TeachingClassEntity();
        existing.setId(30L);
        existing.setInstitutionId(1L);
        existing.setCampusId(1L);
        when(teachingClassMapper.selectById(30L)).thenReturn(existing);

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.updateClass(30L, classRequest("  ")));

        assertEquals("班级编码不能为空", error.getMessage());
        verifyNoInteractions(rules);
        verify(teachingClassMapper, never()).updateById(any(TeachingClassEntity.class));
    }

    @Test
    void 每个登记资源的创建请求都不许再挂非空校验() {
        List<String> offenders = new ArrayList<>();
        CodeRuleTestSupport.fullRegistry().all().forEach((resourceCode, spec) -> {
            Class<?> dto = CREATE_DTO_BY_RESOURCE.get(resourceCode);
            if (dto == null) {
                offenders.add(resourceCode + " 没有登记创建请求类，判空有没有下沉没人守");
                return;
            }
            String property = camelOf(spec.columnName());
            Optional<RecordComponent> component = Arrays.stream(dto.getRecordComponents())
                .filter(item -> item.getName().equals(property))
                .findFirst();
            if (component.isEmpty()) {
                offenders.add(dto.getSimpleName() + " 没有 " + property + " 这一列，编号规则接不到它");
                return;
            }
            boolean rejectsBlankBeforeService = Arrays.stream(component.get().getAnnotations())
                .anyMatch(annotation -> annotation.annotationType() == NotBlank.class
                    || annotation.annotationType() == NotNull.class);
            if (rejectsBlankBeforeService) {
                offenders.add(dto.getSimpleName() + "." + property
                    + " 还挂着非空校验，空表单进不到 Service，规则启用了也不会补号");
            }
        });

        assertTrue(offenders.isEmpty(), String.join(";", offenders));
    }

    /** 按登记的默认格式拼出号段内的第 N 个号：现有最大同号与期望的下一号都由它得出，前缀与位数不写死两遍。 */
    private static String codeOf(String resourceCode, long sequence) {
        CodeRuleSpec spec = CodeRuleTestSupport.fullRegistry().require(resourceCode);
        return CodePattern.format(
            CodePattern.segment(spec.prefix(), spec.separator(), spec.datePattern(), LocalDate.now()),
            sequence, spec.serialLength());
    }

    /** 与 MyBatis-Plus 的驼峰下划线口径一致：org_code -> orgCode。 */
    private static String camelOf(String column) {
        StringBuilder sb = new StringBuilder(column.length());
        boolean upperNext = false;
        for (int i = 0; i < column.length(); i++) {
            char c = column.charAt(i);
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private InstitutionServiceImpl institution(CodeRuleService codeRuleService) {
        return new InstitutionServiceImpl(institutionMapper, codeRuleService);
    }

    private CampusServiceImpl campus(CodeRuleService codeRuleService) {
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        return new CampusServiceImpl(campusMapper, institutionMapper, organizationDict, codeRuleService);
    }

    private StudentServiceImpl student(CodeRuleService codeRuleService) {
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        return new StudentServiceImpl(studentMapper, institutionMapper, educationDict, codeRuleService,
            mock(ClassStudentMapper.class), teachingClassMapper);
    }

    private TeachingClassServiceImpl classService(CodeRuleService codeRuleService) {
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        CampusEntity campus = new CampusEntity();
        campus.setId(1L);
        campus.setInstitutionId(1L);
        when(campusMapper.selectById(1L)).thenReturn(campus);
        return new TeachingClassServiceImpl(teachingClassMapper, institutionMapper, campusMapper,
            mock(TermMapper.class), mock(TeacherMapper.class), mock(CourseMapper.class),
            mock(ClassTypeMapper.class), mock(ClassStudentMapper.class),
            mock(ClassChangeNoticeService.class), mock(ClassTeacherService.class), codeRuleService);
    }

    private static InstitutionCreateRequest institutionRequest(String orgCode) {
        return new InstitutionCreateRequest(orgCode, "示例机构", null, null, null, null, null, null,
            null, null, null, null, null);
    }

    private static CampusCreateRequest campusRequest(String campusCode) {
        return new CampusCreateRequest(1L, campusCode, "主校区", null, null, null, null, null, null,
            "某路 1 号", null, null, null, null, null);
    }

    private static StudentCreateRequest studentRequest(String studentNo) {
        return new StudentCreateRequest(1L, studentNo, "李同学", null, null, "李家长",
            "13900000000", null, null, null);
    }

    private static TeachingClassCreateRequest classRequest(String classCode) {
        return new TeachingClassCreateRequest(1L, 1L, null, null, null, classCode, "主讲班", null, 30,
            null, null, null, null, null, null, null, null);
    }
}
