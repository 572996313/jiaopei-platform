package com.cornerstone.module.education;

import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.module.education.domain.entity.ClassChangeNoticeEntity;
import com.cornerstone.module.education.domain.entity.ClassTeacherEntity;
import com.cornerstone.module.education.domain.entity.ClassTypeEntity;
import com.cornerstone.module.education.domain.entity.CourseCategoryEntity;
import com.cornerstone.module.education.domain.entity.CourseEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.StudentParentEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ClassTeacherCreateRequest;
import com.cornerstone.module.education.dto.ClassTypeCreateRequest;
import com.cornerstone.module.education.dto.CourseCategoryCreateRequest;
import com.cornerstone.module.education.dto.CourseCreateRequest;
import com.cornerstone.module.education.dto.StudentCreateRequest;
import com.cornerstone.module.education.dto.StudentParentCreateRequest;
import com.cornerstone.module.education.dto.TeacherCreateRequest;
import com.cornerstone.module.education.dto.TeachingClassCreateRequest;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.ClassTeacherMapper;
import com.cornerstone.module.education.mapper.ClassTypeMapper;
import com.cornerstone.module.education.mapper.ClassChangeNoticeMapper;
import com.cornerstone.module.education.mapper.CourseCategoryMapper;
import com.cornerstone.module.education.mapper.CourseMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.StudentParentMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.ClassChangeNoticeService;
import com.cornerstone.module.education.service.impl.ClassChangeNoticeServiceImpl;
import com.cornerstone.module.education.service.impl.ClassTeacherServiceImpl;
import com.cornerstone.module.education.service.impl.ClassTypeServiceImpl;
import com.cornerstone.module.education.service.impl.CourseCategoryServiceImpl;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.education.service.impl.CourseServiceImpl;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.module.education.service.impl.StudentServiceImpl;
import com.cornerstone.module.education.service.impl.StudentParentServiceImpl;
import com.cornerstone.module.education.service.impl.TeacherServiceImpl;
import com.cornerstone.module.education.service.impl.TeachingClassServiceImpl;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.module.organization.mapper.TermMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class EducationThirdMainlineValidationTest {

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private ClassStudentMapper classStudentMapper;

    @Mock
    private CourseCategoryMapper courseCategoryMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private ClassTypeMapper classTypeMapper;

    @Mock
    private ClassTeacherMapper classTeacherMapper;

    @Mock
    private ClassChangeNoticeMapper classChangeNoticeMapper;

    @Mock
    private StudentParentMapper studentParentMapper;

    @Mock
    private InstitutionMapper institutionMapper;

    @Mock
    private CampusMapper campusMapper;

    @Mock
    private UserAccountMapper userAccountMapper;

    @Mock
    private TermMapper termMapper;

    // 字典校验器读 SystemDictService；请求里字典字段为空时只走"空值落 NULL"分支，无需打桩
    private final SystemDictService systemDictService = mock(SystemDictService.class);
    private final DictValueValidator dictValueValidator = new DictValueValidator(systemDictService);

    @Mock
    private ClassChangeNoticeService classChangeNoticeService;

    @Test
    void createTeacherRejectsDuplicateTeacherCode() {
        TeacherServiceImpl service = new TeacherServiceImpl(teacherMapper, institutionMapper, campusMapper,
            userAccountMapper, dictValueValidator);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(teacherMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createTeacher(teacherRequest("T001")));

        verify(teacherMapper, never()).insert(any(TeacherEntity.class));
    }

    @Test
    void createStudentRejectsDuplicateStudentNo() {
        StudentServiceImpl service = new StudentServiceImpl(studentMapper, institutionMapper, campusMapper, dictValueValidator);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(studentMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createStudent(studentRequest("S001", LocalDate.of(2018, 1, 1))));

        verify(studentMapper, never()).insert(any(StudentEntity.class));
    }

    @Test
    void createStudentRejectsFutureBirthday() {
        StudentServiceImpl service = new StudentServiceImpl(studentMapper, institutionMapper, campusMapper, dictValueValidator);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());

        assertThrows(BusinessException.class, () -> service.createStudent(studentRequest("S002", LocalDate.now().plusDays(1))));

        verify(studentMapper, never()).insert(any(StudentEntity.class));
    }

    @Test
    void createClassIgnoresRequestedCurrentStudents() {
        TeachingClassServiceImpl service = new TeachingClassServiceImpl(
            teachingClassMapper,
            institutionMapper,
            campusMapper,
            termMapper,
            teacherMapper,
            courseMapper,
            classTypeMapper,
            classStudentMapper,
            classChangeNoticeService
        );
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(campusMapper.selectById(1L)).thenReturn(campus());

        service.createClass(classRequest("C001", "一班", 10, 11));

        ArgumentCaptor<TeachingClassEntity> captor = ArgumentCaptor.forClass(TeachingClassEntity.class);
        verify(teachingClassMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getCurrentStudents());
    }

    @Test
    void createClassRejectsDuplicateClassNameInCampus() {
        TeachingClassServiceImpl service = new TeachingClassServiceImpl(
            teachingClassMapper,
            institutionMapper,
            campusMapper,
            termMapper,
            teacherMapper,
            courseMapper,
            classTypeMapper,
            classStudentMapper,
            classChangeNoticeService
        );
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(campusMapper.selectById(1L)).thenReturn(campus());
        when(teachingClassMapper.selectCount(any())).thenReturn(0L, 1L);

        assertThrows(BusinessException.class, () -> service.createClass(classRequest("C002", "一班", 30, 0)));

        verify(teachingClassMapper, never()).insert(any(TeachingClassEntity.class));
    }

    @Test
    void createCourseCategoryRejectsDuplicateCategoryCode() {
        CourseCategoryServiceImpl service = new CourseCategoryServiceImpl(courseCategoryMapper, institutionMapper);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(courseCategoryMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createCategory(courseCategoryRequest("CAT-001")));

        verify(courseCategoryMapper, never()).insert(any(CourseCategoryEntity.class));
    }

    @Test
    void createCourseRejectsCategoryFromAnotherInstitution() {
        CourseServiceImpl service = new CourseServiceImpl(courseMapper, courseCategoryMapper, institutionMapper, new DictValueValidator(mock(SystemDictService.class)));
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        CourseCategoryEntity category = new CourseCategoryEntity();
        category.setId(1L);
        category.setInstitutionId(2L);
        when(courseCategoryMapper.selectById(1L)).thenReturn(category);

        assertThrows(BusinessException.class, () -> service.createCourse(courseRequest()));

        verify(courseMapper, never()).insert(any(CourseEntity.class));
    }

    @Test
    void createClassTypeRejectsMaxStudentsLessThanMinStudents() {
        ClassTypeServiceImpl service = new ClassTypeServiceImpl(classTypeMapper, institutionMapper, new DictValueValidator(mock(SystemDictService.class)));
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());

        assertThrows(BusinessException.class, () -> service.createClassType(classTypeRequest(4, 3)));

        verify(classTypeMapper, never()).insert(any(ClassTypeEntity.class));
    }

    @Test
    void createClassRejectsCourseFromAnotherInstitution() {
        TeachingClassServiceImpl service = new TeachingClassServiceImpl(
            teachingClassMapper,
            institutionMapper,
            campusMapper,
            termMapper,
            teacherMapper,
            courseMapper,
            classTypeMapper,
            classStudentMapper,
            classChangeNoticeService
        );
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(campusMapper.selectById(1L)).thenReturn(campus());
        CourseEntity course = new CourseEntity();
        course.setId(1L);
        course.setInstitutionId(2L);
        when(courseMapper.selectById(1L)).thenReturn(course);

        assertThrows(BusinessException.class, () -> service.createClass(classRequest("C003", "二班", 20, 0, 1L, null)));

        verify(teachingClassMapper, never()).insert(any(TeachingClassEntity.class));
    }

    @Test
    void updateClassRejectsTeacherChangeFromClassProfile() {
        TeachingClassServiceImpl service = new TeachingClassServiceImpl(
            teachingClassMapper,
            institutionMapper,
            campusMapper,
            termMapper,
            teacherMapper,
            courseMapper,
            classTypeMapper,
            classStudentMapper,
            classChangeNoticeService
        );
        TeachingClassEntity existing = teachingClass(30, 10);
        existing.setTeacherId(8L);
        when(teachingClassMapper.selectById(20L)).thenReturn(existing);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(campusMapper.selectById(1L)).thenReturn(campus());
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(9L);
        teacher.setInstitutionId(1L);
        when(teacherMapper.selectById(9L)).thenReturn(teacher);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.updateClass(
            20L,
            new TeachingClassCreateRequest(
                1L,
                1L,
                null,
                null,
                null,
                "C020",
                "正式班",
                9L,
                30,
                10,
                "active",
                LocalDate.now(),
                null
            )
        ));

        verify(teachingClassMapper, never()).updateById(any(TeachingClassEntity.class));
        assertEquals(
            "主讲教师只能在班级教师关系中变更，请先在班级教师关系里切换主讲教师",
            exception.getMessage()
        );
    }

    @Test
    void createClassTeacherRejectsTeacherFromAnotherInstitution() {
        ClassTeacherServiceImpl service = new ClassTeacherServiceImpl(
            classTeacherMapper,
            teachingClassMapper,
            teacherMapper,
            institutionMapper,
            classChangeNoticeService,
            dictValueValidator
        );
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass(30, 0));
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(10L);
        teacher.setInstitutionId(2L);
        when(teacherMapper.selectById(10L)).thenReturn(teacher);

        assertThrows(BusinessException.class, () -> service.createClassTeacher(classTeacherRequest()));

        verify(classTeacherMapper, never()).insert(any(ClassTeacherEntity.class));
    }

    @Test
    void createPrimaryClassTeacherSyncsTeachingClassTeacherId() {
        ClassTeacherServiceImpl service = new ClassTeacherServiceImpl(
            classTeacherMapper,
            teachingClassMapper,
            teacherMapper,
            institutionMapper,
            classChangeNoticeService,
            dictValueValidator
        );
        TeachingClassEntity teachingClass = teachingClass(30, 0);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass);
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(10L);
        teacher.setInstitutionId(1L);
        when(teacherMapper.selectById(10L)).thenReturn(teacher);
        when(classTeacherMapper.selectCount(any())).thenReturn(0L, 0L);
        when(classTeacherMapper.selectOne(any())).thenReturn(classTeacherEntity(10L, Boolean.TRUE));
        when(systemDictService.listItems("teacher_role"))
            .thenReturn(dictItems("lead", "assistant", "substitute"));

        service.createClassTeacher(classTeacherRequest());

        ArgumentCaptor<TeachingClassEntity> captor = ArgumentCaptor.forClass(TeachingClassEntity.class);
        verify(teachingClassMapper).updateById(captor.capture());
        assertEquals(10L, captor.getValue().getTeacherId());
        verify(classChangeNoticeService).syncClassNotices(1L, 20L);
    }

    @Test
    void createStudentParentAutoDemotesExistingPrimaryParent() {
        StudentParentServiceImpl service = new StudentParentServiceImpl(studentParentMapper, studentMapper, institutionMapper, dictValueValidator);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(studentMapper.selectById(10L)).thenReturn(student());
        StudentParentEntity existingPrimary = new StudentParentEntity();
        existingPrimary.setId(900L);
        existingPrimary.setStudentId(10L);
        existingPrimary.setIsPrimary(Boolean.TRUE);
        when(studentParentMapper.selectCount(any())).thenReturn(0L);
        when(studentParentMapper.selectList(any())).thenReturn(java.util.List.of(existingPrimary));
        when(studentParentMapper.selectOne(any()))
            .thenReturn(null)
            .thenReturn(studentParentEntity("李妈妈", "13900000000", Boolean.TRUE));

        service.createStudentParent(studentParentRequest());

        verify(studentParentMapper).updateById(existingPrimary);
        assertEquals(Boolean.FALSE, existingPrimary.getIsPrimary());
        verify(studentParentMapper).insert(any(StudentParentEntity.class));
    }

    @Test
    void createPrimaryStudentParentSyncsStudentGuardianFields() {
        StudentParentServiceImpl service = new StudentParentServiceImpl(studentParentMapper, studentMapper, institutionMapper, dictValueValidator);
        StudentEntity student = student();
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(studentMapper.selectById(10L)).thenReturn(student);
        when(studentParentMapper.selectCount(any())).thenReturn(0L, 0L);
        when(studentParentMapper.selectOne(any())).thenReturn(studentParentEntity("李妈妈", "13900000000", Boolean.TRUE));

        service.createStudentParent(studentParentRequest());

        ArgumentCaptor<StudentEntity> captor = ArgumentCaptor.forClass(StudentEntity.class);
        verify(studentMapper).updateById(captor.capture());
        assertEquals("李妈妈", captor.getValue().getGuardianName());
        assertEquals("13900000000", captor.getValue().getGuardianPhone());
    }

    @Test
    void deletePrimaryClassTeacherClearsTeachingClassTeacherId() {
        ClassTeacherServiceImpl service = new ClassTeacherServiceImpl(
            classTeacherMapper,
            teachingClassMapper,
            teacherMapper,
            institutionMapper,
            classChangeNoticeService,
            dictValueValidator
        );
        ClassTeacherEntity relation = new ClassTeacherEntity();
        relation.setId(401L);
        relation.setClassId(20L);
        relation.setTeacherId(10L);
        relation.setIsPrimary(Boolean.TRUE);
        TeachingClassEntity teachingClass = teachingClass(30, 0);
        teachingClass.setTeacherId(10L);
        ClassTeacherEntity fallback = new ClassTeacherEntity();
        fallback.setId(402L);
        fallback.setClassId(20L);
        fallback.setTeacherId(11L);
        fallback.setTeacherRole("lead");
        fallback.setStatus("active");
        when(classTeacherMapper.selectById(401L)).thenReturn(relation);
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass);
        when(classTeacherMapper.selectOne(any())).thenReturn(null);
        when(classTeacherMapper.selectList(any())).thenReturn(java.util.List.of(fallback));

        service.deleteClassTeacher(401L);

        ArgumentCaptor<TeachingClassEntity> captor = ArgumentCaptor.forClass(TeachingClassEntity.class);
        verify(teachingClassMapper).updateById(captor.capture());
        assertEquals(11L, captor.getValue().getTeacherId());
        verify(classTeacherMapper).updateById(fallback);
        assertEquals(Boolean.TRUE, fallback.getIsPrimary());
        verify(classChangeNoticeService).recordPrimaryTeacherChanged(
            teachingClass,
            402L,
            "班级主讲教师已自动切换到新的有效教师。",
            "请确认排课、课表与授课安排是否需要同步调整。"
        );
        verify(classChangeNoticeService).syncClassNotices(1L, 20L);
    }

    @Test
    void createClassTriggersNoticeSyncAfterInsert() {
        TeachingClassServiceImpl service = new TeachingClassServiceImpl(
            teachingClassMapper,
            institutionMapper,
            campusMapper,
            termMapper,
            teacherMapper,
            courseMapper,
            classTypeMapper,
            classStudentMapper,
            classChangeNoticeService
        );
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(campusMapper.selectById(1L)).thenReturn(campus());
        when(teachingClassMapper.selectCount(any())).thenReturn(0L, 0L);
        doAnswer(invocation -> {
            TeachingClassEntity entity = invocation.getArgument(0);
            entity.setId(20L);
            return 1;
        }).when(teachingClassMapper).insert(any(TeachingClassEntity.class));

        service.createClass(classRequest("C010", "提醒班", 20, 0));

        verify(classChangeNoticeService).syncClassNotices(1L, 20L);
    }

    @Test
    void updateClassAllowsTeacherIdWhenUnchangedAndSyncsNotices() {
        TeachingClassServiceImpl service = new TeachingClassServiceImpl(
            teachingClassMapper,
            institutionMapper,
            campusMapper,
            termMapper,
            teacherMapper,
            courseMapper,
            classTypeMapper,
            classStudentMapper,
            classChangeNoticeService
        );
        TeachingClassEntity existing = teachingClass(30, 18);
        existing.setTeacherId(10L);
        existing.setClassCode("C021");
        existing.setClassName("原班级");
        when(teachingClassMapper.selectById(20L)).thenReturn(existing);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(campusMapper.selectById(1L)).thenReturn(campus());
        CourseEntity course = new CourseEntity();
        course.setId(2L);
        course.setInstitutionId(1L);
        when(courseMapper.selectById(2L)).thenReturn(course);
        ClassTypeEntity classType = new ClassTypeEntity();
        classType.setId(3L);
        classType.setInstitutionId(1L);
        when(classTypeMapper.selectById(3L)).thenReturn(classType);
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(10L);
        teacher.setInstitutionId(1L);
        when(teacherMapper.selectById(10L)).thenReturn(teacher);
        when(teachingClassMapper.selectCount(any())).thenReturn(0L, 0L);
        when(classStudentMapper.selectCount(any())).thenReturn(19L);

        TeachingClassEntity updated = service.updateClass(
            20L,
            new TeachingClassCreateRequest(
                1L,
                1L,
                null,
                2L,
                3L,
                "C021",
                "新班级",
                10L,
                24,
                18,
                "active",
                LocalDate.now(),
                "同步正式 update"
            )
        );

        assertEquals(10L, updated.getTeacherId());
        assertEquals(2L, updated.getCourseId());
        assertEquals(3L, updated.getClassTypeId());
        assertEquals(24, updated.getCapacityLimit());
        assertEquals(19, updated.getCurrentStudents());
        verify(teachingClassMapper).updateById(existing);
        verify(classChangeNoticeService).syncClassNotices(1L, 20L);
    }

    @Test
    void deleteClassRejectsWhenClassHasActiveStudents() {
        TeachingClassServiceImpl service = new TeachingClassServiceImpl(
            teachingClassMapper,
            institutionMapper,
            campusMapper,
            termMapper,
            teacherMapper,
            courseMapper,
            classTypeMapper,
            classStudentMapper,
            classChangeNoticeService
        );
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass(30, 12));
        when(classStudentMapper.selectCount(any())).thenReturn(3L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.deleteClass(20L));

        verify(teachingClassMapper, never()).deleteById(any(Long.class));
        assertTrue(exception.getMessage().contains("在读学员"));
    }

    @Test
    void deleteClassRemovesByIdWhenNoActiveStudents() {
        TeachingClassServiceImpl service = new TeachingClassServiceImpl(
            teachingClassMapper,
            institutionMapper,
            campusMapper,
            termMapper,
            teacherMapper,
            courseMapper,
            classTypeMapper,
            classStudentMapper,
            classChangeNoticeService
        );
        when(teachingClassMapper.selectById(20L)).thenReturn(teachingClass(30, 0));
        when(classStudentMapper.selectCount(any())).thenReturn(0L);

        service.deleteClass(20L);

        verify(teachingClassMapper).deleteById(20L);
    }

    @Test
    void syncClassNoticesCreatesMissingCourseClassTypeAndPrimaryTeacher() {
        ClassChangeNoticeServiceImpl service = new ClassChangeNoticeServiceImpl(
            classChangeNoticeMapper,
            teachingClassMapper,
            classTeacherMapper
        );
        TeachingClassEntity teachingClass = teachingClass(30, 0);
        when(teachingClassMapper.selectList(any())).thenReturn(java.util.List.of(teachingClass));
        when(classTeacherMapper.selectOne(any())).thenReturn(null);
        when(classChangeNoticeMapper.selectList(any()))
            .thenReturn(java.util.List.of())
            .thenReturn(java.util.List.of())
            .thenReturn(java.util.List.of());

        service.syncClassNotices(1L, 20L);

        verify(classChangeNoticeMapper, times(3)).insert(any(ClassChangeNoticeEntity.class));
    }

    @Test
    void syncClassNoticesResolvesMissingPrimaryTeacherWhenTeacherExists() {
        ClassChangeNoticeServiceImpl service = new ClassChangeNoticeServiceImpl(
            classChangeNoticeMapper,
            teachingClassMapper,
            classTeacherMapper
        );
        TeachingClassEntity teachingClass = teachingClass(30, 0);
        teachingClass.setTeacherId(10L);
        ClassChangeNoticeEntity pendingNotice = new ClassChangeNoticeEntity();
        pendingNotice.setId(701L);
        pendingNotice.setClassId(20L);
        pendingNotice.setNoticeType("missing_primary_teacher");
        pendingNotice.setNoticeStatus("pending");
        when(teachingClassMapper.selectList(any())).thenReturn(java.util.List.of(teachingClass));
        when(classChangeNoticeMapper.selectList(any()))
            .thenReturn(java.util.List.of())
            .thenReturn(java.util.List.of())
            .thenReturn(java.util.List.of(pendingNotice));

        service.syncClassNotices(1L, 20L);

        verify(classChangeNoticeMapper).updateById(pendingNotice);
        assertEquals("resolved", pendingNotice.getNoticeStatus());
    }

    @Test
    void deletePrimaryStudentParentClearsStudentGuardianFields() {
        StudentParentServiceImpl service = new StudentParentServiceImpl(studentParentMapper, studentMapper, institutionMapper, dictValueValidator);
        StudentParentEntity parent = new StudentParentEntity();
        parent.setId(402L);
        parent.setStudentId(10L);
        parent.setParentName("李妈妈");
        parent.setParentPhone("13900000000");
        parent.setIsPrimary(Boolean.TRUE);
        StudentEntity student = student();
        student.setGuardianName("李妈妈");
        student.setGuardianPhone("13900000000");
        when(studentParentMapper.selectById(402L)).thenReturn(parent);
        when(studentMapper.selectById(10L)).thenReturn(student);
        when(studentParentMapper.selectOne(any()))
            .thenReturn(null)
            .thenReturn(studentParentEntity("李爸爸", "13700000000", Boolean.TRUE));

        service.deleteStudentParent(402L);

        ArgumentCaptor<StudentEntity> captor = ArgumentCaptor.forClass(StudentEntity.class);
        verify(studentMapper).updateById(captor.capture());
        assertEquals("李爸爸", captor.getValue().getGuardianName());
        assertEquals("13700000000", captor.getValue().getGuardianPhone());
    }

    private TeacherCreateRequest teacherRequest(String teacherCode) {
        return new TeacherCreateRequest(
            1L,
            null,
            teacherCode,
            "张老师",
            "13800000000",
            null,
            null,
            null,
            null,
            LocalDate.now(),
            null
        );
    }

    private StudentCreateRequest studentRequest(String studentNo, LocalDate birthday) {
        return new StudentCreateRequest(
            1L,
            null,
            studentNo,
            "李同学",
            null,
            birthday,
            "李家长",
            "13900000000",
            null,
            LocalDate.now(),
            null
        );
    }

    private TeachingClassCreateRequest classRequest(String classCode, String className, int capacityLimit,
                                                    int currentStudents) {
        return new TeachingClassCreateRequest(
            1L,
            1L,
            null,
            null,
            null,
            classCode,
            className,
            null,
            capacityLimit,
            currentStudents,
            null,
            LocalDate.now(),
            null
        );
    }

    private TeachingClassCreateRequest classRequest(String classCode, String className, int capacityLimit,
                                                    int currentStudents, Long courseId, Long classTypeId) {
        return new TeachingClassCreateRequest(
            1L,
            1L,
            null,
            courseId,
            classTypeId,
            classCode,
            className,
            null,
            capacityLimit,
            currentStudents,
            null,
            LocalDate.now(),
            null
        );
    }

    private CourseCategoryCreateRequest courseCategoryRequest(String categoryCode) {
        return new CourseCategoryCreateRequest(1L, categoryCode, "艺术", null, 1, null);
    }

    private CourseCreateRequest courseRequest() {
        return new CourseCreateRequest(1L, 1L, "COURSE-001", "钢琴启蒙", "音乐", null, null, null);
    }

    private ClassTypeCreateRequest classTypeRequest(int minStudents, Integer maxStudents) {
        return new ClassTypeCreateRequest(1L, "ONE_TO_FOUR", "1对4", null, minStudents, maxStudents, null, null);
    }

    private ClassTeacherCreateRequest classTeacherRequest() {
        return new ClassTeacherCreateRequest(1L, 20L, 10L, "lead", Boolean.TRUE, LocalDate.now(), null, null, null);
    }

    private StudentParentCreateRequest studentParentRequest() {
        return new StudentParentCreateRequest(1L, 10L, "李妈妈", null, "13900000000", null, Boolean.TRUE, Boolean.FALSE, null, null);
    }

    private StudentEntity student() {
        StudentEntity student = new StudentEntity();
        student.setId(10L);
        student.setInstitutionId(1L);
        student.setCampusId(1L);
        return student;
    }

    private StudentParentEntity studentParentEntity(String parentName, String parentPhone, Boolean isPrimary) {
        StudentParentEntity entity = new StudentParentEntity();
        entity.setId(901L);
        entity.setStudentId(10L);
        entity.setParentName(parentName);
        entity.setParentPhone(parentPhone);
        entity.setIsPrimary(isPrimary);
        entity.setStatus("active");
        return entity;
    }

    private ClassTeacherEntity classTeacherEntity(Long teacherId, Boolean isPrimary) {
        ClassTeacherEntity entity = new ClassTeacherEntity();
        entity.setId(801L);
        entity.setClassId(20L);
        entity.setTeacherId(teacherId);
        entity.setTeacherRole("lead");
        entity.setIsPrimary(isPrimary);
        entity.setStatus("active");
        return entity;
    }

    private TeachingClassEntity teachingClass(int capacityLimit, int currentStudents) {
        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(20L);
        teachingClass.setInstitutionId(1L);
        teachingClass.setCampusId(1L);
        teachingClass.setCapacityLimit(capacityLimit);
        teachingClass.setCurrentStudents(currentStudents);
        return teachingClass;
    }

    private CampusEntity campus() {
        CampusEntity campus = new CampusEntity();
        campus.setId(1L);
        campus.setInstitutionId(1L);
        return campus;
    }

    /** 构造与字典种子一致的启用条目，供 DictValueValidator 的 SystemDictService 打桩用。 */
    private java.util.List<DictItemView> dictItems(String... itemValues) {
        return java.util.Arrays.stream(itemValues)
            .map(value -> new DictItemView(null, null, value, value, value, 0, "active", null))
            .toList();
    }
}
