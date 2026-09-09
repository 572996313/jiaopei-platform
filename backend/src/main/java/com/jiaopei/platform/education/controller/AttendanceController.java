package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.AttendanceEntity;
import com.jiaopei.platform.education.dto.AttendanceCreateRequest;
import com.jiaopei.platform.education.dto.AttendanceLessonBatchSaveRequest;
import com.jiaopei.platform.education.dto.AttendanceLessonPageView;
import com.jiaopei.platform.education.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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
    @RequiresPermission("education:read")
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
    @RequiresPermission("education:read")
    public ApiResponse<AttendanceLessonPageView> lessonPage(@RequestParam String lessonId) {
        return ApiResponse.success("课次点名页已加载",
            attendanceService.getLessonAttendancePage(ExternalIdParser.parseRequired(lessonId, "lessonId")));
    }

    @Operation(summary = "创建考勤")
    @PostMapping
    @RequiresPermission("education:write")
    public ApiResponse<AttendanceEntity> create(@Valid @RequestBody AttendanceCreateRequest request) {
        return ApiResponse.success("考勤已创建", attendanceService.createAttendance(request));
    }

    @Operation(summary = "保存课次点名")
    @PostMapping("/lesson-batch")
    @RequiresPermission("education:write")
    public ApiResponse<AttendanceLessonPageView> saveLessonAttendance(@Valid @RequestBody AttendanceLessonBatchSaveRequest request) {
        return ApiResponse.success("课次点名已保存", attendanceService.saveLessonAttendance(request));
    }
}
