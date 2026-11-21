package com.cornerstone.module.education.fee.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.fee.dto.FeeMonthlyOverrideRequest;
import com.cornerstone.module.education.fee.dto.FeeMonthlyView;
import com.cornerstone.module.education.fee.dto.FeeRecalculateRequest;
import com.cornerstone.module.education.fee.service.FeeLedgerService;
import com.cornerstone.module.education.fee.service.FeeMonthlyService;
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
@RequestMapping("/api/education/fee-monthly")
@Tag(name = "收费管理")
public class FeeMonthlyController {

    private final FeeMonthlyService feeMonthlyService;
    private final FeeLedgerService feeLedgerService;

    public FeeMonthlyController(FeeMonthlyService feeMonthlyService, FeeLedgerService feeLedgerService) {
        this.feeMonthlyService = feeMonthlyService;
        this.feeLedgerService = feeLedgerService;
    }

    @Operation(summary = "获取逐月发生额", description = "按账期升序、项目排序号排；手工覆盖行带 manualOverride 标记")
    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<FeeMonthlyView>> list(@RequestParam(required = false) String ledgerId) {
        return ApiResponse.success("逐月发生额已加载",
            feeMonthlyService.listByLedger(ExternalIdParser.parseRequired(ledgerId, "ledgerId")));
    }

    @Operation(summary = "手工覆盖当月发生额",
        description = "发生额留空表示只改月份系数、生效值跟随引擎重算（不产生覆盖）；发生额非空才置 manualOverride=1，重算不再改这一行")
    @PutMapping("/{ledgerItemId}/{billingMonth}")
    @RequiresPermission("education:fee:write")
    public ApiResponse<FeeMonthlyView> override(
        @PathVariable Long ledgerItemId,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billingMonth,
        @Valid @RequestBody FeeMonthlyOverrideRequest request) {
        return ApiResponse.success("当月发生额已覆盖",
            feeMonthlyService.override(ledgerItemId, billingMonth, request));
    }

    @Operation(summary = "撤销当月发生额覆盖",
        description = "只撤销生效发生额的手工覆盖，月份系数保留；发生额回到引擎自动值，随后重算整份台账")
    @DeleteMapping("/{ledgerItemId}/{billingMonth}")
    @RequiresPermission("education:fee:write")
    public ApiResponse<FeeMonthlyView> revert(
        @PathVariable Long ledgerItemId,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billingMonth) {
        return ApiResponse.success("发生额覆盖已撤销",
            feeMonthlyService.revert(ledgerItemId, billingMonth));
    }

    @Operation(summary = "按当前出勤与标准重算", description = "传台账只重算一份；否则同时传学期与班级批量重算。已手工覆盖的发生额不动")
    @PostMapping("/recalculate")
    @RequiresPermission("education:fee:write")
    public ApiResponse<Integer> recalculate(@RequestBody FeeRecalculateRequest request) {
        return ApiResponse.success("重算完成", feeLedgerService.recalculate(request));
    }
}
