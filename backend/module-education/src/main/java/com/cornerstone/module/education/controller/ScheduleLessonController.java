package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.dto.ScheduleConflictCheckRequest;
import com.cornerstone.module.education.dto.ScheduleConflictCheckResponse;
import com.cornerstone.module.education.dto.ScheduleLessonPageQuery;
import com.cornerstone.module.education.dto.ScheduleLessonRequest;
import com.cornerstone.module.education.dto.ScheduleLessonView;
import com.cornerstone.module.education.service.ScheduleLessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
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

    @Operation(summary = "获取排课列表")
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

    @Operation(summary = "排课课次分页列表", description = "按机构/校区/班级/日期筛选,分页返回历史课次")
    @GetMapping("/lessons/page")
    @RequiresPermission("education:read")
    public ApiResponse<PageResult<ScheduleLessonView>> page(@Valid @ParameterObject ScheduleLessonPageQuery query) {
        return ApiResponse.success("排课分页列表已加载", scheduleLessonService.pageLessons(query));
    }

    @Operation(summary = "待补课课次分页列表", description = "仅返回已停课且未安排补课的课次,SQL 层收敛,分页返回")
    @GetMapping("/lessons/pending-makeup-page")
    @RequiresPermission("education:read")
    public ApiResponse<PageResult<ScheduleLessonView>> pendingMakeupPage(
        @Valid @ParameterObject ScheduleLessonPageQuery query) {
        return ApiResponse.success("待补课分页列表已加载", scheduleLessonService.pagePendingMakeupLessons(query));
    }

    @Operation(summary = "获取排课详情")
    @GetMapping("/lessons/{id}")
    @RequiresPermission("education:read")
    public ApiResponse<ScheduleLessonView> get(@PathVariable Long id) {
        return ApiResponse.success("排课详情已加载", scheduleLessonService.getLesson(id));
    }

    @Operation(summary = "创建排课")
    @PostMapping("/lessons")
    @RequiresPermission("education:write")
    public ApiResponse<ScheduleLessonView> create(@Valid @RequestBody ScheduleLessonRequest request) {
        return ApiResponse.success("排课已创建", scheduleLessonService.createLesson(request));
    }

    @Operation(summary = "更新排课")
    @PutMapping("/lessons/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<ScheduleLessonView> update(@PathVariable Long id, @Valid @RequestBody ScheduleLessonRequest request) {
        return ApiResponse.success("排课已更新", scheduleLessonService.updateLesson(id, request));
    }

    @Operation(summary = "删除排课")
    @DeleteMapping("/lessons/{id}")
    @RequiresPermission("education:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scheduleLessonService.deleteLesson(id);
        return ApiResponse.success("排课已删除", null);
    }

    @Operation(summary = "获取日课表")
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

    @Operation(summary = "获取周课表")
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

    @Operation(summary = "排课冲突检查")
    @PostMapping("/conflicts/check")
    @RequiresPermission("education:write")
    public ApiResponse<ScheduleConflictCheckResponse> check(@Valid @RequestBody ScheduleConflictCheckRequest request) {
        return ApiResponse.success("排课冲突检查完成", scheduleLessonService.checkConflicts(request));
    }
}
