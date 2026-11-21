package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.module.education.dto.TimeSlotLessonCleanupResponse;
import com.cornerstone.module.education.dto.TimeSlotLessonRefView;
import com.cornerstone.module.education.service.TimeSlotLessonRefService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 节次（时间段）的排课引用查询与清理，供组织模块的「节次明细」页调用。
 *
 * <p>数据归排课域，所以接口挂在这里；节次删不掉时"是谁在挡"只有这几张表答得出来。
 */
@RestController
@RequestMapping("/api/education/time-slot-lessons")
@Tag(name = "教务管理")
public class TimeSlotLessonRefController {

    private final TimeSlotLessonRefService timeSlotLessonRefService;

    public TimeSlotLessonRefController(TimeSlotLessonRefService timeSlotLessonRefService) {
        this.timeSlotLessonRefService = timeSlotLessonRefService;
    }

    @Operation(summary = "列出引用某节次的课次",
        description = "含已删除的课次(它们在课表页不显示,却以外键指着本节次),未删除的排在前面;机构范围取登录用户")
    @GetMapping
    public ApiResponse<List<TimeSlotLessonRefView>> list(@RequestParam Long timeSlotId) {
        return ApiResponse.success("引用课次已加载",
            timeSlotLessonRefService.listRefsByTimeSlot(AuthContext.requireUser().orgId(), timeSlotId));
    }

    @Operation(summary = "清理引用某节次的已删除课次",
        description = "只物理删除 deleted=1 的课次及其教师关系与考勤行,未删除的课次一律不碰;清理到没有引用后该节次才能删除")
    @DeleteMapping("/tombstones")
    @RequiresPermission("education:schedule:write")
    public ApiResponse<TimeSlotLessonCleanupResponse> purgeTombstones(@RequestParam Long timeSlotId) {
        return ApiResponse.success("已删除课次已清理",
            timeSlotLessonRefService.purgeTombstoneLessons(AuthContext.requireUser().orgId(), timeSlotId));
    }
}
