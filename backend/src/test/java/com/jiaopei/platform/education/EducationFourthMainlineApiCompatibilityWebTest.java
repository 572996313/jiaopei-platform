package com.jiaopei.platform.education;

import com.jiaopei.platform.auth.aspect.PermissionGuardAspect;
import com.jiaopei.platform.auth.security.RestAccessDeniedHandler;
import com.jiaopei.platform.auth.security.RestAuthenticationEntryPoint;
import com.jiaopei.platform.auth.security.StubAuthenticationFilter;
import com.jiaopei.platform.auth.service.PermissionService;
import com.jiaopei.platform.common.web.ApiResponseAdvice;
import com.jiaopei.platform.common.web.GlobalExceptionHandler;
import com.jiaopei.platform.common.web.TraceIdFilter;
import com.jiaopei.platform.config.SecurityConfig;
import com.jiaopei.platform.education.controller.AttendanceController;
import com.jiaopei.platform.education.controller.ClassStudentController;
import com.jiaopei.platform.education.controller.EnrollmentController;
import com.jiaopei.platform.education.domain.entity.AttendanceEntity;
import com.jiaopei.platform.education.domain.entity.ClassStudentEntity;
import com.jiaopei.platform.education.domain.entity.EnrollmentEntity;
import com.jiaopei.platform.education.dto.AttendanceCreateRequest;
import com.jiaopei.platform.education.dto.ClassStudentCreateRequest;
import com.jiaopei.platform.education.dto.EnrollmentCreateRequest;
import com.jiaopei.platform.education.service.AttendanceService;
import com.jiaopei.platform.education.service.ClassStudentService;
import com.jiaopei.platform.education.service.EnrollmentService;
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
    EnrollmentController.class,
    ClassStudentController.class,
    AttendanceController.class
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
class EducationFourthMainlineApiCompatibilityWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnrollmentService enrollmentService;

    @MockBean
    private ClassStudentService classStudentService;

    @MockBean
    private AttendanceService attendanceService;

    @MockBean
    private PermissionService permissionService;

    @BeforeEach
    void allowStubUser() {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(enrollmentService.listEnrollments(any(), any(), any())).willReturn(List.of());
        given(classStudentService.listClassStudents(any(), any())).willReturn(List.of());
        given(attendanceService.listAttendances(any(), any(), any())).willReturn(List.of());
    }

    @Test
    void fourthMainlineListEndpointsAcceptBusinessStyleIds() throws Exception {
        mockMvc.perform(get("/api/education/enrollments")
                .param("institutionId", "INST-001")
                .param("classId", "CLS-020")
                .param("studentId", "ST-010")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(enrollmentService).listEnrollments(1L, 20L, 10L);

        mockMvc.perform(get("/api/education/class-students")
                .param("classId", "CLS-020")
                .param("studentId", "ST-010")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(classStudentService).listClassStudents(20L, 10L);

        mockMvc.perform(get("/api/education/attendance")
                .param("classId", "CLS-020")
                .param("studentId", "ST-010")
                .param("attendanceDate", "2026-06-08")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk());
        verify(attendanceService).listAttendances(20L, 10L, java.time.LocalDate.of(2026, 6, 8));
    }

    @Test
    void createEnrollmentAcceptsSnakeCaseFrontendPayload() throws Exception {
        given(enrollmentService.createEnrollment(any())).willAnswer(invocation -> {
            EnrollmentCreateRequest request = invocation.getArgument(0);
            EnrollmentEntity entity = new EnrollmentEntity();
            entity.setId(300L);
            entity.setInstitutionId(request.institutionId());
            entity.setClassId(request.classId());
            entity.setStudentId(request.studentId());
            entity.setEnrollmentNo(request.enrollmentNo());
            return entity;
        });

        mockMvc.perform(post("/api/education/enrollments")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institution_id": "INST-001",
                      "class_id": "CLS-020",
                      "student_id": "ST-010",
                      "enrollment_no": "ENR-001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.institutionId").value(1))
            .andExpect(jsonPath("$.data.classId").value(20))
            .andExpect(jsonPath("$.data.studentId").value(10));
    }

    @Test
    void createClassStudentAndAttendanceAcceptSnakeCasePayloads() throws Exception {
        given(classStudentService.createClassStudent(any())).willAnswer(invocation -> {
            ClassStudentCreateRequest request = invocation.getArgument(0);
            ClassStudentEntity entity = new ClassStudentEntity();
            entity.setId(400L);
            entity.setClassId(request.classId());
            entity.setStudentId(request.studentId());
            return entity;
        });
        given(attendanceService.createAttendance(any())).willAnswer(invocation -> {
            AttendanceCreateRequest request = invocation.getArgument(0);
            AttendanceEntity entity = new AttendanceEntity();
            entity.setId(500L);
            entity.setClassId(request.classId());
            entity.setStudentId(request.studentId());
            entity.setAttendanceDate(request.attendanceDate());
            entity.setAttendanceStatus(request.attendanceStatus());
            return entity;
        });

        mockMvc.perform(post("/api/education/class_student")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "class_id": "CLS-020",
                      "student_id": "ST-010",
                      "joined_at": "2026-06-08"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.classId").value(20))
            .andExpect(jsonPath("$.data.studentId").value(10));

        mockMvc.perform(post("/api/education/attendance")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "class_id": "CLS-020",
                      "student_id": "ST-010",
                      "attendance_date": "2026-06-08",
                      "attendance_status": "present"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.classId").value(20))
            .andExpect(jsonPath("$.data.studentId").value(10))
            .andExpect(jsonPath("$.data.attendanceStatus").value("present"));
    }
}
