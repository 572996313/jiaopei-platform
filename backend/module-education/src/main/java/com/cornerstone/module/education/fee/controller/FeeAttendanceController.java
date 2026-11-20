package com.cornerstone.module.education.fee.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.fee.dto.FeeAttendanceOverrideRequest;
import com.cornerstone.module.education.fee.dto.FeeAttendanceSyncRequest;
import com.cornerstone.module.education.fee.dto.FeeAttendanceSyncResult;
import com.cornerstone.module.education.fee.dto.FeeAttendanceView;
import com.cornerstone.module.education.fee.service.FeeAttendanceService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/education/fee-attendance")
@Tag(name = "收费管理")
public class FeeAttendanceController {

    private final FeeAttendanceService feeAttendanceService;

    public FeeAttendanceController(FeeAttendanceService feeAttendanceService) {
        this.feeAttendanceService = feeAttendanceService;
    }

    @Operation(summary = "获取月度出勤", description = "按账期升序；autoDays 是考勤汇总值，attendanceDays 是生效值")
    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<FeeAttendanceView>> list(@RequestParam(required = false) String ledgerId) {
        return ApiResponse.success("月度出勤已加载",
            feeAttendanceService.listByLedger(ExternalIdParser.parseRequired(ledgerId, "ledgerId")));
    }

    @Operation(summary = "手工覆盖出勤天数", description = "覆盖后该月不再随考勤同步变化；按天项目的发生额会立即重算")
    @PutMapping("/{ledgerId}/{billingMonth}")
    @RequiresPermission("education:fee:write")
    public ApiResponse<FeeAttendanceView> override(
        @PathVariable Long ledgerId,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billingMonth,
        @Valid @RequestBody FeeAttendanceOverrideRequest request) {
        return ApiResponse.success("出勤天数已覆盖",
            feeAttendanceService.override(ledgerId, billingMonth, request));
    }

    @Operation(summary = "撤销出勤天数覆盖",
        description = "出勤天数回到最近一次考勤汇总值（autoDays），按天项目的发生额会立即重算")
    @DeleteMapping("/{ledgerId}/{billingMonth}")
    @RequiresPermission("education:fee:write")
    public ApiResponse<FeeAttendanceView> revert(
        @PathVariable Long ledgerId,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billingMonth) {
        return ApiResponse.success("出勤覆盖已撤销",
            feeAttendanceService.revert(ledgerId, billingMonth));
    }

    @Operation(summary = "从考勤点名汇总出勤天数",
        description = "只算 present + late；COUNT(DISTINCT 日期)。传台账只同步一份，否则同时传学期与班级批量同步")
    @PostMapping("/sync")
    @RequiresPermission("education:fee:write")
    public ApiResponse<FeeAttendanceSyncResult> sync(@RequestBody FeeAttendanceSyncRequest request) {
        return ApiResponse.success("出勤已从考勤汇总", feeAttendanceService.sync(request));
    }
}
