package com.cornerstone.module.education;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cornerstone.core.auth.aspect.PermissionGuardAspect;
import com.cornerstone.core.auth.security.RestAccessDeniedHandler;
import com.cornerstone.core.auth.security.RestAuthenticationEntryPoint;
import com.cornerstone.core.auth.security.StubAuthenticationFilter;
import com.cornerstone.core.auth.service.PermissionService;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.web.ApiResponseAdvice;
import com.cornerstone.core.common.web.GlobalExceptionHandler;
import com.cornerstone.core.common.web.TraceIdFilter;
import com.cornerstone.core.config.SecurityConfig;
import com.cornerstone.module.education.schedule.controller.SchedulePlanController;
import com.cornerstone.module.education.schedule.dto.SchedulePlanCommitResponse;
import com.cornerstone.module.education.schedule.dto.SchedulePlanPreviewResponse;
import com.cornerstone.module.education.schedule.dto.SchedulePlanView;
import com.cornerstone.module.education.schedule.service.SchedulePlanService;
import com.cornerstone.support.WebMvcSecurityTestConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SchedulePlanController.class)
@Import({
    // 业务模块改由 AutoConfiguration 装配，@WebMvcTest 切片会关掉自动配置，控制器要显式 @Import。
    SchedulePlanController.class,
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
class SchedulePlanControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SchedulePlanService schedulePlanService;

    @MockBean
    private PermissionService permissionService;

    @Test
    void previewRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/education/schedule-plans/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PREVIEW_BODY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void previewReturnsItems() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(schedulePlanService.preview(any())).willReturn(new SchedulePlanPreviewResponse(
            List.of(new SchedulePlanPreviewResponse.Item(1, LocalDate.of(2026, 6, 1), 1, 500L,
                "上1", null, null, false, List.of(), false, true)),
            1, 0, 0, List.of()));

        mockMvc.perform(post("/api/education/schedule-plans/preview")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PREVIEW_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].weekNo").value(1))
            .andExpect(jsonPath("$.data.items[0].suggestedChecked").value(true));
    }

    @Test
    void commitReturnsCreatedCount() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(schedulePlanService.commit(any())).willReturn(new SchedulePlanCommitResponse(900L, 8, 1,
            List.of("2026-06-01 时段500：该计划已生成过课次，跳过")));

        mockMvc.perform(post("/api/education/schedule-plans/commit")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(COMMIT_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createdCount").value(8))
            .andExpect(jsonPath("$.data.skippedCount").value(1));
    }

    @Test
    void commitConflictRendersBadRequestMessage() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(schedulePlanService.commit(any())).willThrow(new BusinessException(ResultCode.BAD_REQUEST,
            "勾选课次存在冲突，本次提交未生效：2026-06-08：该教室在此日此节次已被占用"));

        mockMvc.perform(post("/api/education/schedule-plans/commit")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(COMMIT_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("该教室在此日此节次已被占用")));
    }

    @Test
    void cancelLessonsReturnsCount() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(schedulePlanService.cancelPlanLessons(anyLong())).willReturn(3);

        mockMvc.perform(delete("/api/education/schedule-plans/900/lessons")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(3));

        verify(schedulePlanService).cancelPlanLessons(900L);
    }

    @Test
    void updateReturnsView() throws Exception {
        given(permissionService.hasPermission(any(), anyString())).willReturn(true);
        given(schedulePlanService.updatePlan(anyLong(), any())).willReturn(new SchedulePlanView(
            900L, 1L, 10L, 20L, 100L, 300L, List.of(400L),
            List.of(new com.cornerstone.module.education.schedule.dto.ScheduleSlotCell(2, 500L)),
            "every_week", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
            List.of(), "active", null, null));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/education/schedule-plans/900")
                .header("Authorization", "Bearer stub-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PREVIEW_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(900))
            .andExpect(jsonPath("$.data.slots[0].weekday").value(2));
    }

    private static final String PREVIEW_BODY = """
        {
          "institutionId": 1,
          "campusId": 10,
          "termId": 20,
          "classId": 100,
          "classroomId": 300,
          "teacherIds": [400],
          "slots": [{"weekday": 2, "timeSlotId": 500}],
          "startDate": "2026-06-01",
          "endDate": "2026-06-14"
        }
        """;

    private static final String COMMIT_BODY = """
        {
          "plan": {
            "institutionId": 1,
            "campusId": 10,
            "termId": 20,
            "classId": 100,
            "classroomId": 300,
            "teacherIds": [400],
            "slots": [{"weekday": 2, "timeSlotId": 500}],
            "startDate": "2026-06-01",
            "endDate": "2026-06-14"
          },
          "lessons": [{"lessonDate": "2026-06-09", "timeSlotId": 500}]
        }
        """;
}
