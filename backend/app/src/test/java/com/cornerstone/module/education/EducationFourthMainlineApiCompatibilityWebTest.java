package com.cornerstone.module.education;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cornerstone.core.auth.aspect.PermissionGuardAspect;
import com.cornerstone.core.auth.security.RestAccessDeniedHandler;
import com.cornerstone.core.auth.security.RestAuthenticationEntryPoint;
import com.cornerstone.core.auth.security.StubAuthenticationFilter;
import com.cornerstone.core.auth.service.PermissionService;
import com.cornerstone.core.common.web.ApiResponseAdvice;
import com.cornerstone.core.common.web.GlobalExceptionHandler;
import com.cornerstone.core.common.web.TraceIdFilter;
import com.cornerstone.core.config.SecurityConfig;
import com.cornerstone.module.education.attendance.controller.AttendanceController;
import com.cornerstone.module.education.attendance.dto.AttendanceBatchItemRequest;
import com.cornerstone.module.education.attendance.dto.AttendanceCreateRequest;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonInfoView;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonPageView;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonStudentView;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonSummaryView;
import com.cornerstone.module.education.attendance.service.AttendanceService;
import com.cornerstone.module.education.domain.entity.AttendanceEntity;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.schedule.controller.ScheduleLessonController;
import com.cornerstone.module.education.schedule.dto.ScheduleConflictCheckResponse;
import com.cornerstone.module.education.schedule.dto.ScheduleLessonRequest;
import com.cornerstone.module.education.schedule.dto.ScheduleLessonView;
import com.cornerstone.module.education.schedule.service.ScheduleLessonService;
import com.cornerstone.module.education.teachingclass.controller.ClassStudentController;
import com.cornerstone.module.education.teachingclass.dto.ClassStudentCreateRequest;
import com.cornerstone.module.education.teachingclass.service.ClassStudentService;
import com.cornerstone.support.WebMvcSecurityTestConfig;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
    ClassStudentController.class,
    AttendanceController.class,
    ScheduleLessonController.class
})
@Import({
    // 业务模块改由 AutoConfiguration 装配，@WebMvcTest 切片会关掉自动配置，
    // 所以下面这些业务控制器必须在切片里显式 @Import，否则接口 404。
    ClassStudentController.class,
    AttendanceController.class,
    ScheduleLessonController.class,
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
    private ClassStudentService classStudentService;

    @MockBean
    private AttendanceService attendanceService;

    @MockBean
    private ScheduleLessonService scheduleLessonService;

    @MockBean
    private PermissionService permissionService;

    @BeforeEach
    void allowStubUser() {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(classStudentService.listClassStudents(any(), any())).willReturn(List.of());
        given(attendanceService.listAttendances(any(), any(), any(), any())).willReturn(List.of());
        given(scheduleLessonService.listLessons(any(), any(), any(), any(), any())).willReturn(List.of());
    }

    @Test
    void fourthMainlineListEndpointsAcceptBusinessStyleIds() throws Exception {
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
        verify(attendanceService).listAttendances(null, 20L, 10L, LocalDate.of(2026, 6, 8));
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

    @Test
    void lessonAttendanceEndpointsAcceptBusinessStyleLessonIds() throws Exception {
        AttendanceLessonPageView response = lessonAttendancePage();
        given(attendanceService.getLessonAttendancePage(300L)).willReturn(response);
        given(attendanceService.saveLessonAttendance(any())).willReturn(response);

        mockMvc.perform(get("/api/education/attendance/lesson-page")
                .param("lessonId", "LESSON-300")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("课次点名页已加载"))
            .andExpect(jsonPath("$.data.lesson.lessonId").value(300))
            .andExpect(jsonPath("$.data.summary.expectedCount").value(1))
            .andExpect(jsonPath("$.data.summary.lateCount").value(1))
            .andExpect(jsonPath("$.data.students[0].attendanceStatus").value("late"));
        verify(attendanceService).getLessonAttendancePage(300L);

        mockMvc.perform(post("/api/education/attendance/lesson-batch")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lesson_id": "LESSON-300",
                      "items": [
                        {
                          "student_id": "ST-010",
                          "attendance_status": "late",
                          "source": "manual",
                          "remark": "traffic"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("课次点名已保存"))
            .andExpect(jsonPath("$.data.lesson.lessonId").value(300))
            .andExpect(jsonPath("$.data.students[0].studentId").value(10));
        verify(attendanceService).saveLessonAttendance(new com.cornerstone.module.education.attendance.dto.AttendanceLessonBatchSaveRequest(
            300L,
            List.of(new AttendanceBatchItemRequest(10L, "late", "manual", "traffic"))
        ));
    }

    @Test
    void scheduleLessonCreateUpdateAndConflictCheckAcceptExecutionChangeStatuses() throws Exception {
        ScheduleLessonView lessonView = new ScheduleLessonView(
            700L,
            1L,
            2L,
            3L,
            20L,
            200L,
            500L,
            null,
            LocalDate.of(2026, 6, 15),
            1,
            LocalTime.of(9, 0),
            LocalTime.of(10, 0),
            "数学补课",
            "makeup",
            "cancelled_makeup",
            680L,
            false,
            List.of(400L),
            "周末补课",
            List.of("教师「李老师」 周一·上1 为禁排时段")
        );
        given(scheduleLessonService.createLesson(any())).willReturn(lessonView);
        given(scheduleLessonService.updateLesson(any(), any())).willReturn(new ScheduleLessonView(
            700L,
            1L,
            2L,
            3L,
            20L,
            200L,
            500L,
            null,
            LocalDate.of(2026, 6, 15),
            1,
            LocalTime.of(9, 0),
            LocalTime.of(10, 0),
            "数学调课",
            "rescheduled",
            null,
            null,
            false,
            List.of(400L),
            "调到周末",
            List.of()
        ));
        given(scheduleLessonService.checkConflicts(any())).willReturn(
            new ScheduleConflictCheckResponse(false, List.of(), List.of(), List.of())
        );

        mockMvc.perform(post("/api/education/schedules/lessons")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionId": 1,
                      "campusId": 2,
                      "termId": 3,
                      "classId": 20,
                      "classroomId": 200,
                      "timeSlotId": 500,
                      "lessonDate": "2026-06-15",
                      "lessonTitle": "数学补课",
                      "lessonStatus": "makeup",
                      "changeType": "cancelled_makeup",
                      "relatedLessonId": 680,
                      "teacherIds": [400],
                      "remark": "周末补课"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lessonStatus").value("makeup"))
            .andExpect(jsonPath("$.data.changeType").value("cancelled_makeup"))
            .andExpect(jsonPath("$.data.relatedLessonId").value(680));

        mockMvc.perform(put("/api/education/schedules/lessons/700")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionId": 1,
                      "campusId": 2,
                      "termId": 3,
                      "classId": 20,
                      "classroomId": 200,
                      "timeSlotId": 500,
                      "lessonDate": "2026-06-15",
                      "lessonTitle": "数学调课",
                      "lessonStatus": "rescheduled",
                      "changeType": null,
                      "relatedLessonId": null,
                      "teacherIds": [400],
                      "remark": "调到周末"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lessonStatus").value("rescheduled"));

        mockMvc.perform(post("/api/education/schedules/conflicts/check")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "excludeLessonId": 700,
                      "institutionId": 1,
                      "campusId": 2,
                      "termId": 3,
                      "classId": 20,
                      "classroomId": 200,
                      "timeSlotId": 500,
                      "lessonDate": "2026-06-15",
                      "teacherIds": [400]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.conflict").value(false));

        verify(scheduleLessonService).createLesson(any(ScheduleLessonRequest.class));
        verify(scheduleLessonService).updateLesson(any(), any(ScheduleLessonRequest.class));
    }

    @Test
    void scheduleLessonListSupportsPendingMakeupOnlyFilter() throws Exception {
        given(scheduleLessonService.listLessons(1L, 2L, 20L, LocalDate.of(2026, 6, 15), true)).willReturn(List.of(
            new ScheduleLessonView(
                701L,
                1L,
                2L,
                3L,
                20L,
                200L,
                500L,
                null,
                LocalDate.of(2026, 6, 15),
                1,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                "停课待补",
                "cancelled",
                null,
                null,
                true,
                List.of(400L),
                "待安排补课",
                List.of()
            )
        ));

        mockMvc.perform(get("/api/education/schedules/lessons")
                .param("institutionId", "1")
                .param("campusId", "2")
                .param("classId", "20")
                .param("lessonDate", "2026-06-15")
                .param("pendingMakeupOnly", "true")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].lessonStatus").value("cancelled"))
            .andExpect(jsonPath("$.data[0].pendingMakeup").value(true));

        verify(scheduleLessonService).listLessons(1L, 2L, 20L, LocalDate.of(2026, 6, 15), true);
    }

    private AttendanceLessonPageView lessonAttendancePage() {
        return new AttendanceLessonPageView(
            new AttendanceLessonInfoView(
                300L,
                1L,
                2L,
                20L,
                "Class 20",
                LocalDate.of(2026, 6, 8),
                LocalTime.of(9, 0),
                LocalTime.of(10, 30),
                "Math",
                "scheduled",
                null
            ),
            List.of(new AttendanceLessonStudentView(10L, "ST-010", "Alice", 500L, "late", "manual", "traffic")),
            new AttendanceLessonSummaryView(1, 1, 0, 0, 0, 1),
            null
        );
    }
}
