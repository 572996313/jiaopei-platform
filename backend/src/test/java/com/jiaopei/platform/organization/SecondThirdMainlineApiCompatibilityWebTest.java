package com.jiaopei.platform.organization;

import com.jiaopei.platform.auth.aspect.PermissionGuardAspect;
import com.jiaopei.platform.auth.security.RestAccessDeniedHandler;
import com.jiaopei.platform.auth.security.RestAuthenticationEntryPoint;
import com.jiaopei.platform.auth.security.StubAuthenticationFilter;
import com.jiaopei.platform.auth.service.PermissionService;
import com.jiaopei.platform.common.web.ApiResponseAdvice;
import com.jiaopei.platform.common.web.GlobalExceptionHandler;
import com.jiaopei.platform.common.web.TraceIdFilter;
import com.jiaopei.platform.config.SecurityConfig;
import com.jiaopei.platform.education.controller.ClassTeacherController;
import com.jiaopei.platform.education.controller.ClassTypeController;
import com.jiaopei.platform.education.controller.ClassChangeNoticeController;
import com.jiaopei.platform.education.controller.CourseCategoryController;
import com.jiaopei.platform.education.controller.CourseController;
import com.jiaopei.platform.education.controller.StudentController;
import com.jiaopei.platform.education.controller.StudentParentController;
import com.jiaopei.platform.education.controller.TeacherController;
import com.jiaopei.platform.education.controller.TeachingClassController;
import com.jiaopei.platform.education.domain.entity.ClassTeacherEntity;
import com.jiaopei.platform.education.domain.entity.ClassTypeEntity;
import com.jiaopei.platform.education.domain.entity.CourseCategoryEntity;
import com.jiaopei.platform.education.domain.entity.CourseEntity;
import com.jiaopei.platform.education.domain.entity.StudentParentEntity;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.ClassTeacherCreateRequest;
import com.jiaopei.platform.education.dto.ClassTypeCreateRequest;
import com.jiaopei.platform.education.dto.CourseCategoryCreateRequest;
import com.jiaopei.platform.education.dto.CourseCreateRequest;
import com.jiaopei.platform.education.dto.StudentParentCreateRequest;
import com.jiaopei.platform.education.dto.TeacherCreateRequest;
import com.jiaopei.platform.education.dto.TeachingClassCreateRequest;
import com.jiaopei.platform.education.service.ClassTeacherService;
import com.jiaopei.platform.education.service.ClassTypeService;
import com.jiaopei.platform.education.service.ClassChangeNoticeService;
import com.jiaopei.platform.education.service.CourseCategoryService;
import com.jiaopei.platform.education.service.CourseService;
import com.jiaopei.platform.education.service.StudentService;
import com.jiaopei.platform.education.service.StudentParentService;
import com.jiaopei.platform.education.service.TeacherService;
import com.jiaopei.platform.education.service.TeachingClassService;
import com.jiaopei.platform.organization.controller.ClassroomController;
import com.jiaopei.platform.organization.controller.TermController;
import com.jiaopei.platform.organization.controller.TimeSlotController;
import com.jiaopei.platform.organization.service.ClassroomService;
import com.jiaopei.platform.organization.service.TermService;
import com.jiaopei.platform.organization.service.TimeSlotService;
import com.jiaopei.platform.support.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
    ClassroomController.class,
    TermController.class,
    TimeSlotController.class,
    TeacherController.class,
    StudentController.class,
    TeachingClassController.class,
    CourseCategoryController.class,
    CourseController.class,
    ClassTypeController.class,
    ClassTeacherController.class,
    ClassChangeNoticeController.class,
    StudentParentController.class
})
@Import({
    SecurityConfig.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    StubAuthenticationFilter.class,
    TraceIdFilter.class,
    ApiResponseAdvice.class,
    GlobalExceptionHandler.class,
    PermissionGuardAspect.class,
    WebMvcSecurityTestConfig.class
})
class SecondThirdMainlineApiCompatibilityWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClassroomService classroomService;

    @MockBean
    private TermService termService;

    @MockBean
    private TimeSlotService timeSlotService;

    @MockBean
    private TeacherService teacherService;

    @MockBean
    private StudentService studentService;

    @MockBean
    private TeachingClassService teachingClassService;

    @MockBean
    private CourseCategoryService courseCategoryService;

    @MockBean
    private CourseService courseService;

    @MockBean
    private ClassTypeService classTypeService;

    @MockBean
    private ClassTeacherService classTeacherService;

    @MockBean
    private ClassChangeNoticeService classChangeNoticeService;

    @MockBean
    private StudentParentService studentParentService;

    @MockBean
    private PermissionService permissionService;

    @BeforeEach
    void allowStubUser() {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(classroomService.listClassrooms(any())).willReturn(List.of());
        given(termService.listTerms(any())).willReturn(List.of());
        given(timeSlotService.listTimeSlots(any())).willReturn(List.of());
        given(teacherService.listTeachers(any(), any())).willReturn(List.of());
        given(studentService.listStudents(any(), any())).willReturn(List.of());
        given(teachingClassService.listClasses(any(), any(), any())).willReturn(List.of());
        given(courseCategoryService.listCategories(any())).willReturn(List.of());
        given(courseService.listCourses(any(), any())).willReturn(List.of());
        given(classTypeService.listClassTypes(any())).willReturn(List.of());
        given(classTeacherService.listClassTeachers(any(), any())).willReturn(List.of());
        given(classChangeNoticeService.listNotices(any(), any(), any())).willReturn(List.of());
        given(studentParentService.listStudentParents(any(), any())).willReturn(List.of());
    }

    @Test
    void secondMainlineListEndpointsAcceptBusinessStyleIds() throws Exception {
        mockMvc.perform(get("/api/organizations/classrooms")
                .param("campusId", "CMP-001")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        verify(classroomService).listClassrooms(1L);

        mockMvc.perform(get("/api/organizations/terms")
                .param("institutionId", "INST-001")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(termService).listTerms(1L);

        mockMvc.perform(get("/api/organizations/time-slots")
                .param("institutionId", "INST-001")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(timeSlotService).listTimeSlots(1L);
    }

    @Test
    void thirdMainlineListEndpointsAcceptBusinessStyleIds() throws Exception {
        mockMvc.perform(get("/api/education/teachers")
                .param("institutionId", "INST-001")
                .param("campusId", "CMP-001")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(teacherService).listTeachers(1L, 1L);

        mockMvc.perform(get("/api/education/students")
                .param("institutionId", "INST-001")
                .param("campusId", "CMP-001")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(studentService).listStudents(1L, 1L);

        mockMvc.perform(get("/api/education/classes")
                .param("institutionId", "INST-001")
                .param("campusId", "CMP-001")
                .param("termId", "TERM-001")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(teachingClassService).listClasses(1L, 1L, 1L);

        mockMvc.perform(get("/api/education/course-categories")
                .param("institutionId", "INST-001")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(courseCategoryService).listCategories(1L);

        mockMvc.perform(get("/api/education/courses")
                .param("institutionId", "INST-001")
                .param("categoryId", "CAT-001")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(courseService).listCourses(1L, 1L);

        mockMvc.perform(get("/api/education/class-types")
                .param("institutionId", "INST-001")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(classTypeService).listClassTypes(1L);

        mockMvc.perform(get("/api/education/class-teachers")
                .param("classId", "CLS-001")
                .param("teacherId", "TCH-001")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(classTeacherService).listClassTeachers(1L, 1L);

        mockMvc.perform(get("/api/education/student-parents")
                .param("institutionId", "INST-001")
                .param("studentId", "ST-001")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(studentParentService).listStudentParents(1L, 1L);

        mockMvc.perform(get("/api/education/class-change-notices")
                .param("institutionId", "INST-001")
                .param("classId", "CLS-001")
                .param("noticeStatus", "pending")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(classChangeNoticeService).listNotices(1L, 1L, "pending");
    }

    @Test
    void createTeacherAcceptsSnakeCaseFrontendPayload() throws Exception {
        given(teacherService.createTeacher(any())).willAnswer(invocation -> {
            TeacherCreateRequest request = invocation.getArgument(0);
            TeacherEntity entity = new TeacherEntity();
            entity.setId(100L);
            entity.setInstitutionId(request.institutionId());
            entity.setCampusId(request.campusId());
            entity.setTeacherCode(request.teacherCode());
            entity.setTeacherName(request.teacherName());
            entity.setPhone(request.phone());
            return entity;
        });

        mockMvc.perform(post("/api/education/teachers")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institution_id": "INST-001",
                      "campus_id": "CMP-001",
                      "teacher_code": "TC-009",
                      "teacher_name": "王老师",
                      "mobile": "13800000009",
                      "employment_type": "全职",
                      "status": "active"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.institutionId").value(1))
            .andExpect(jsonPath("$.data.campusId").value(1))
            .andExpect(jsonPath("$.data.phone").value("13800000009"));
    }

    @Test
    void createClassAcceptsSnakeCasePayloadWithoutCapacityLimit() throws Exception {
        given(teachingClassService.createClass(any())).willAnswer(invocation -> {
            TeachingClassCreateRequest request = invocation.getArgument(0);
            TeachingClassEntity entity = new TeachingClassEntity();
            entity.setId(200L);
            entity.setInstitutionId(request.institutionId());
            entity.setCampusId(request.campusId());
            entity.setTermId(request.termId());
            entity.setCourseId(request.courseId());
            entity.setClassTypeId(request.classTypeId());
            entity.setClassCode(request.classCode());
            entity.setClassName(request.className());
            entity.setCapacityLimit(request.capacityLimit() == null ? 0 : request.capacityLimit());
            return entity;
        });

        mockMvc.perform(post("/api/education/classes")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institution_id": "INST-001",
                      "campus_id": "CMP-001",
                      "term_id": "TERM-001",
                      "course_id": "COURSE-001",
                      "class_type_id": "TYPE-001",
                      "class_code": "CL-009",
                      "class_name": "数学提升 B 班",
                      "status": "draft"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.institutionId").value(1))
            .andExpect(jsonPath("$.data.campusId").value(1))
            .andExpect(jsonPath("$.data.termId").value(1))
            .andExpect(jsonPath("$.data.courseId").value(1))
            .andExpect(jsonPath("$.data.classTypeId").value(1))
            .andExpect(jsonPath("$.data.capacityLimit").value(0));
    }

    @Test
    void updateClassAcceptsSnakeCasePayload() throws Exception {
        given(teachingClassService.updateClass(any(), any())).willAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            TeachingClassCreateRequest request = invocation.getArgument(1);
            TeachingClassEntity entity = new TeachingClassEntity();
            entity.setId(id);
            entity.setInstitutionId(request.institutionId());
            entity.setCampusId(request.campusId());
            entity.setTermId(request.termId());
            entity.setCourseId(request.courseId());
            entity.setClassTypeId(request.classTypeId());
            entity.setClassCode(request.classCode());
            entity.setClassName(request.className());
            entity.setTeacherId(request.teacherId());
            entity.setCapacityLimit(request.capacityLimit());
            entity.setCurrentStudents(request.currentStudents());
            entity.setStatus(request.status());
            entity.setRemark(request.remark());
            return entity;
        });

        mockMvc.perform(put("/api/education/classes/200")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institution_id": "INST-001",
                      "campus_id": "CMP-001",
                      "term_id": "TERM-001",
                      "course_id": "COURSE-002",
                      "class_type_id": "TYPE-002",
                      "class_code": "CL-009",
                      "class_name": "数学提升 B 班",
                      "teacher_id": "TCH-001",
                      "capacity_limit": 24,
                      "current_students": 18,
                      "status": "active",
                      "remark": "切到正式维护接口"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(200))
            .andExpect(jsonPath("$.data.courseId").value(2))
            .andExpect(jsonPath("$.data.classTypeId").value(2))
            .andExpect(jsonPath("$.data.teacherId").value(1))
            .andExpect(jsonPath("$.data.capacityLimit").value(24))
            .andExpect(jsonPath("$.data.currentStudents").value(18))
            .andExpect(jsonPath("$.data.status").value("active"))
            .andExpect(jsonPath("$.data.remark").value("切到正式维护接口"));
    }

    @Test
    void createCourseCategoryAndCourseAndClassTypeAcceptSnakeCasePayload() throws Exception {
        given(courseCategoryService.createCategory(any())).willAnswer(invocation -> {
            CourseCategoryCreateRequest request = invocation.getArgument(0);
            CourseCategoryEntity entity = new CourseCategoryEntity();
            entity.setId(301L);
            entity.setInstitutionId(request.institutionId());
            entity.setCategoryCode(request.categoryCode());
            entity.setCategoryName(request.categoryName());
            entity.setSortOrder(request.sortOrder());
            return entity;
        });
        given(courseService.createCourse(any())).willAnswer(invocation -> {
            CourseCreateRequest request = invocation.getArgument(0);
            CourseEntity entity = new CourseEntity();
            entity.setId(302L);
            entity.setInstitutionId(request.institutionId());
            entity.setCategoryId(request.categoryId());
            entity.setCourseCode(request.courseCode());
            entity.setCourseName(request.courseName());
            return entity;
        });
        given(classTypeService.createClassType(any())).willAnswer(invocation -> {
            ClassTypeCreateRequest request = invocation.getArgument(0);
            ClassTypeEntity entity = new ClassTypeEntity();
            entity.setId(303L);
            entity.setInstitutionId(request.institutionId());
            entity.setTypeCode(request.typeCode());
            entity.setTypeName(request.typeName());
            entity.setMinStudents(request.minStudents());
            entity.setMaxStudents(request.maxStudents());
            return entity;
        });

        mockMvc.perform(post("/api/education/course-categories")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institution_id": "INST-001",
                      "category_code": "CAT-001",
                      "category_name": "艺术",
                      "sort_order": 5
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.institutionId").value(1))
            .andExpect(jsonPath("$.data.categoryCode").value("CAT-001"));

        mockMvc.perform(post("/api/education/courses")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institution_id": "INST-001",
                      "category_id": "CAT-001",
                      "course_code": "COURSE-001",
                      "course_name": "钢琴启蒙",
                      "subject_name": "音乐"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.institutionId").value(1))
            .andExpect(jsonPath("$.data.categoryId").value(1))
            .andExpect(jsonPath("$.data.courseCode").value("COURSE-001"));

        mockMvc.perform(post("/api/education/class-types")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institution_id": "INST-001",
                      "type_code": "ONE_TO_FOUR",
                      "type_name": "1对4",
                      "min_students": 2,
                      "max_students": 4
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.institutionId").value(1))
            .andExpect(jsonPath("$.data.typeCode").value("ONE_TO_FOUR"))
            .andExpect(jsonPath("$.data.maxStudents").value(4));
    }

    @Test
    void createClassTeacherAndStudentParentAcceptSnakeCasePayload() throws Exception {
        given(classTeacherService.createClassTeacher(any())).willAnswer(invocation -> {
            ClassTeacherCreateRequest request = invocation.getArgument(0);
            ClassTeacherEntity entity = new ClassTeacherEntity();
            entity.setId(401L);
            entity.setInstitutionId(request.institutionId());
            entity.setClassId(request.classId());
            entity.setTeacherId(request.teacherId());
            entity.setTeacherRole(request.teacherRole());
            entity.setIsPrimary(request.isPrimary());
            return entity;
        });
        given(studentParentService.createStudentParent(any())).willAnswer(invocation -> {
            StudentParentCreateRequest request = invocation.getArgument(0);
            StudentParentEntity entity = new StudentParentEntity();
            entity.setId(402L);
            entity.setInstitutionId(request.institutionId());
            entity.setStudentId(request.studentId());
            entity.setParentName(request.parentName());
            entity.setParentPhone(request.parentPhone());
            entity.setIsPrimary(request.isPrimary());
            return entity;
        });

        mockMvc.perform(post("/api/education/class_teacher")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institution_id": "INST-001",
                      "class_id": "CLS-001",
                      "teacher_id": "TCH-001",
                      "teacher_role": "assistant",
                      "is_primary": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.classId").value(1))
            .andExpect(jsonPath("$.data.teacherId").value(1))
            .andExpect(jsonPath("$.data.teacherRole").value("assistant"))
            .andExpect(jsonPath("$.data.isPrimary").value(true));

        mockMvc.perform(post("/api/education/student_parent")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institution_id": "INST-001",
                      "student_id": "ST-001",
                      "parent_name": "李妈妈",
                      "parent_phone": "13900000000",
                      "is_primary": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.studentId").value(1))
            .andExpect(jsonPath("$.data.parentName").value("李妈妈"))
            .andExpect(jsonPath("$.data.isPrimary").value(true));

        given(classTeacherService.updateClassTeacher(any(), any())).willAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            ClassTeacherCreateRequest request = invocation.getArgument(1);
            ClassTeacherEntity entity = new ClassTeacherEntity();
            entity.setId(id);
            entity.setInstitutionId(request.institutionId());
            entity.setClassId(request.classId());
            entity.setTeacherId(request.teacherId());
            entity.setTeacherRole(request.teacherRole());
            entity.setIsPrimary(request.isPrimary());
            return entity;
        });
        given(studentParentService.updateStudentParent(any(), any())).willAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            StudentParentCreateRequest request = invocation.getArgument(1);
            StudentParentEntity entity = new StudentParentEntity();
            entity.setId(id);
            entity.setInstitutionId(request.institutionId());
            entity.setStudentId(request.studentId());
            entity.setParentName(request.parentName());
            entity.setParentPhone(request.parentPhone());
            entity.setIsPrimary(request.isPrimary());
            return entity;
        });

        mockMvc.perform(put("/api/education/class_teacher/401")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institution_id": "INST-001",
                      "class_id": "CLS-001",
                      "teacher_id": "TCH-002",
                      "teacher_role": "main",
                      "is_primary": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(401))
            .andExpect(jsonPath("$.data.teacherId").value(2));

        mockMvc.perform(put("/api/education/student_parent/402")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institution_id": "INST-001",
                      "student_id": "ST-001",
                      "parent_name": "李爸爸",
                      "parent_phone": "13700000000",
                      "is_primary": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(402))
            .andExpect(jsonPath("$.data.parentName").value("李爸爸"));

        mockMvc.perform(delete("/api/education/class_teacher/401")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Class teacher deleted"));
        verify(classTeacherService).deleteClassTeacher(401L);

        mockMvc.perform(delete("/api/education/student_parent/402")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Student parent deleted"));
        verify(studentParentService).deleteStudentParent(402L);
    }
}
