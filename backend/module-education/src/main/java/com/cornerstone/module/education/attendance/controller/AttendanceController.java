package com.cornerstone.module.education.attendance.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.attendance.dto.AttendanceCreateRequest;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonBatchSaveRequest;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonPageView;
import com.cornerstone.module.education.attendance.dto.AttendanceLessonProgressView;
import com.cornerstone.module.education.attendance.dto.AttendanceStudentDetailResponse;
import com.cornerstone.module.education.attendance.dto.AttendanceSummaryQuery;
import com.cornerstone.module.education.attendance.dto.AttendanceSummaryResponse;
import com.cornerstone.module.education.attendance.service.AttendanceService;
import com.cornerstone.module.education.domain.entity.AttendanceEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/education/attendance")
@Tag(name = "教务管理")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @Operation(summary = "获取考勤列表")
    @GetMapping
    public ApiResponse<List<AttendanceEntity>> list(@RequestParam(required = false) String lessonId,
                                                    @RequestParam(required = false) String classId,
                                                    @RequestParam(required = false) String studentId,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                    LocalDate attendanceDate) {
        return ApiResponse.success("考勤列表已加载", attendanceService.listAttendances(
            ExternalIdParser.parseOptional(lessonId, "lessonId"),
            ExternalIdParser.parseOptional(classId, "classId"),
            ExternalIdParser.parseOptional(studentId, "studentId"),
            attendanceDate
        ));
    }

    @Operation(summary = "获取课次点名页")
    @GetMapping("/lesson-page")
    public ApiResponse<AttendanceLessonPageView> lessonPage(@RequestParam String lessonId) {
        return ApiResponse.success("课次点名页已加载",
            attendanceService.getLessonAttendancePage(ExternalIdParser.parseRequired(lessonId, "lessonId")));
    }

    @Operation(summary = "批量获取课次点名进度", description = "按课次 ID 列表返回应到/已点名人数,供日课表一次拉取,避免逐课次请求点名页")
    @GetMapping("/lesson-progress")
    public ApiResponse<List<AttendanceLessonProgressView>> lessonProgress(@RequestParam List<Long> lessonIds) {
        return ApiResponse.success("课次点名进度已加载", attendanceService.listLessonProgress(lessonIds));
    }

    @Operation(summary = "创建考勤")
    @PostMapping
    @RequiresPermission("education:attendance:manage")
    public ApiResponse<AttendanceEntity> create(@Valid @RequestBody AttendanceCreateRequest request) {
        return ApiResponse.success("考勤已创建", attendanceService.createAttendance(request));
    }

    @Operation(summary = "保存课次点名", description = "持考勤管理码可保存任意课次的点名;只有课次点名码时仅限本人授课的课次")
    @PostMapping("/lesson-batch")
    @RequiresPermission("education:attendance:write")
    public ApiResponse<AttendanceLessonPageView> saveLessonAttendance(@Valid @RequestBody AttendanceLessonBatchSaveRequest request) {
        return ApiResponse.success("课次点名已保存", attendanceService.saveLessonAttendance(request));
    }

    @Operation(summary = "考勤汇总", description = "按日期区间统计点名进度与出勤人次,支持按班级或按学员分组;只统计登录用户所属机构")
    @GetMapping("/summary")
    public ApiResponse<AttendanceSummaryResponse> summary(@Valid @ParameterObject AttendanceSummaryQuery query) {
        return ApiResponse.success("考勤汇总已加载",
            attendanceService.getSummary(AuthContext.requireUser().orgId(), query));
    }

    @Operation(summary = "学员考勤明细", description = "单个学员在日期区间内的应到课次与点名状态流水,供学员档案的考勤抽屉使用")
    @GetMapping("/student-detail")
    public ApiResponse<AttendanceStudentDetailResponse> studentDetail(
        @RequestParam String studentId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.success("学员考勤明细已加载", attendanceService.getStudentDetail(
            AuthContext.requireUser().orgId(),
            ExternalIdParser.parseRequired(studentId, "studentId"),
            dateFrom,
            dateTo));
    }
}
