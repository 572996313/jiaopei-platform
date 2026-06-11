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
import com.jiaopei.platform.education.controller.StudentController;
import com.jiaopei.platform.education.controller.TeacherController;
import com.jiaopei.platform.education.controller.TeachingClassController;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.TeacherCreateRequest;
import com.jiaopei.platform.education.dto.TeachingClassCreateRequest;
import com.jiaopei.platform.education.service.StudentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
    ClassroomController.class,
    TermController.class,
    TimeSlotController.class,
    TeacherController.class,
    StudentController.class,
    TeachingClassController.class
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
                      "class_code": "CL-009",
                      "class_name": "数学提升 B 班",
                      "status": "draft"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.institutionId").value(1))
            .andExpect(jsonPath("$.data.campusId").value(1))
            .andExpect(jsonPath("$.data.termId").value(1))
            .andExpect(jsonPath("$.data.capacityLimit").value(0));
    }
}
