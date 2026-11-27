package com.cornerstone.module.education.schedule.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.module.education.schedule.dto.SchoolCalendarRequest;
import com.cornerstone.module.education.schedule.dto.SchoolCalendarView;
import com.cornerstone.module.education.schedule.service.SchoolCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/api/education/school-calendar")
@Tag(name = "教务管理")
public class SchoolCalendarController {

    private final SchoolCalendarService schoolCalendarService;

    public SchoolCalendarController(SchoolCalendarService schoolCalendarService) {
        this.schoolCalendarService = schoolCalendarService;
    }

    @Operation(summary = "校区日历停排日列表", description = "按机构/校区/年份取停排日;传 campusId 时同时返回全校区(0)那批,与排课提示的读取范围一致,日期升序全量返回(数据量小,不分页)")
    @GetMapping
    public ApiResponse<List<SchoolCalendarView>> list(@RequestParam(required = false) Long institutionId,
                                                     @RequestParam(required = false) Long campusId,
                                                     @RequestParam(required = false) Integer year) {
        return ApiResponse.success("校区日历已加载", schoolCalendarService.listDays(institutionId, campusId, year));
    }

    @Operation(summary = "新增停排日", description = "登记法定假或校区检修等当天不可排课的日子,campusId 留空或 0 表示全校区")
    @PostMapping
    @RequiresPermission("education:schedule:write")
    public ApiResponse<SchoolCalendarView> create(@Valid @RequestBody SchoolCalendarRequest request) {
        return ApiResponse.success("停排日已新增", schoolCalendarService.createDay(request));
    }

    @Operation(summary = "更新停排日", description = "整体覆盖日期、名称与范围;不影响已生成课次")
    @PutMapping("/{id}")
    @RequiresPermission("education:schedule:write")
    public ApiResponse<SchoolCalendarView> update(@PathVariable Long id, @Valid @RequestBody SchoolCalendarRequest request) {
        return ApiResponse.success("停排日已更新", schoolCalendarService.updateDay(id, request));
    }

    @Operation(summary = "删除停排日")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:schedule:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        schoolCalendarService.deleteDay(id);
        return ApiResponse.success("停排日已删除", null);
    }
}
