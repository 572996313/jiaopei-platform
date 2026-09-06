package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.education.dto.ScheduleConflictCheckRequest;
import com.jiaopei.platform.education.dto.ScheduleConflictCheckResponse;
import com.jiaopei.platform.education.dto.ScheduleLessonRequest;
import com.jiaopei.platform.education.dto.ScheduleLessonView;
import com.jiaopei.platform.education.service.ScheduleLessonService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/education/schedules")
@Tag(name = "教务管理")
public class ScheduleLessonController {

    private final ScheduleLessonService scheduleLessonService;

    public ScheduleLessonController(ScheduleLessonService scheduleLessonService) {
        this.scheduleLessonService = scheduleLessonService;
    }

    @GetMapping("/lessons")
    @RequiresPermission("education:read")
    public ApiResponse<List<ScheduleLessonView>> list(@RequestParam(required = false) Long institutionId,
                                                       @RequestParam(required = false) Long campusId,
                                                       @RequestParam(required = false) Long classId,
                                                       @RequestParam(required = false)
                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                       LocalDate lessonDate,
                                                       @RequestParam(required = false) Boolean pendingMakeupOnly) {
        return ApiResponse.success("排课列表已加载",
            scheduleLessonService.listLessons(institutionId, campusId, classId, lessonDate, pendingMakeupOnly));
    }

    @GetMapping("/lessons/{id}")
    @RequiresPermission("education:read")
    public ApiResponse<ScheduleLessonView> get(@PathVariable Long id) {
        return ApiResponse.success("排课详情已加载", scheduleLessonService.getLesson(id));
    }

    @PostMapping("/lessons")
    @RequiresPermission("education:write")
    public ApiResponse<ScheduleLessonView> create(@Valid @RequestBody ScheduleLessonRequest request) {
        return ApiResponse.success("排课已创建", scheduleLessonService.createLesson(request));
    }

    @PutMapping("/lessons/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<ScheduleLessonView> update(@PathVariable Long id, @Valid @RequestBody ScheduleLessonRequest request) {
        return ApiResponse.success("排课已更新", scheduleLessonService.updateLesson(id, request));
    }

    @DeleteMapping("/lessons/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scheduleLessonService.deleteLesson(id);
        return ApiResponse.success("排课已删除", null);
    }

    @GetMapping("/timetable/day")
    @RequiresPermission("education:read")
    public ApiResponse<List<ScheduleLessonView>> day(@RequestParam(required = false) Long institutionId,
                                                     @RequestParam(required = false) Long campusId,
                                                     @RequestParam(required = false) Long classId,
                                                     @RequestParam
                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                     LocalDate lessonDate,
                                                     @RequestParam(required = false) Boolean pendingMakeupOnly) {
        return ApiResponse.success("日课表已加载",
            scheduleLessonService.dayTimetable(institutionId, campusId, classId, lessonDate, pendingMakeupOnly));
    }

    @GetMapping("/timetable/week")
    @RequiresPermission("education:read")
    public ApiResponse<List<ScheduleLessonView>> week(@RequestParam(required = false) Long institutionId,
                                                      @RequestParam(required = false) Long campusId,
                                                      @RequestParam(required = false) Long classId,
                                                      @RequestParam
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                      LocalDate weekStart,
                                                      @RequestParam
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                      LocalDate weekEnd,
                                                      @RequestParam(required = false) Boolean pendingMakeupOnly) {
        return ApiResponse.success("周课表已加载",
            scheduleLessonService.weekTimetable(institutionId, campusId, classId, weekStart, weekEnd, pendingMakeupOnly));
    }

    @PostMapping("/conflicts/check")
    @RequiresPermission("education:write")
    public ApiResponse<ScheduleConflictCheckResponse> check(@Valid @RequestBody ScheduleConflictCheckRequest request) {
        return ApiResponse.success("排课冲突检查完成", scheduleLessonService.checkConflicts(request));
    }
}
