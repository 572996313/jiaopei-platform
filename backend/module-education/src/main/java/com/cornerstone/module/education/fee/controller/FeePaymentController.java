package com.cornerstone.module.education.fee.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.fee.dto.FeePaymentCreateRequest;
import com.cornerstone.module.education.fee.dto.FeePaymentUpdateRequest;
import com.cornerstone.module.education.fee.dto.FeePaymentView;
import com.cornerstone.module.education.fee.service.FeePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/education/fee-payments")
@Tag(name = "收费管理")
public class FeePaymentController {

    private final FeePaymentService feePaymentService;

    public FeePaymentController(FeePaymentService feePaymentService) {
        this.feePaymentService = feePaymentService;
    }

    @Operation(summary = "获取收款流水", description = "按台账或台账明细查询，按收款日期倒序")
    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<FeePaymentView>> list(@RequestParam(required = false) String ledgerId,
                                                  @RequestParam(required = false) String ledgerItemId) {
        return ApiResponse.success("收款流水已加载", feePaymentService.listPayments(
            ExternalIdParser.parseOptional(ledgerId, "ledgerId"),
            ExternalIdParser.parseOptional(ledgerItemId, "ledgerItemId")));
    }

    @Operation(summary = "登记收款", description = "收款单号留空时按 SK-日期-序号 生成；金额允许负数用于冲正")
    @PostMapping
    @RequiresPermission("education:fee:write")
    public ApiResponse<FeePaymentView> create(@Valid @RequestBody FeePaymentCreateRequest request) {
        return ApiResponse.success("收款已登记", feePaymentService.createPayment(request));
    }

    @Operation(summary = "修改收款流水")
    @PutMapping("/{id}")
    @RequiresPermission("education:fee:write")
    public ApiResponse<FeePaymentView> update(@PathVariable Long id,
                                              @Valid @RequestBody FeePaymentUpdateRequest request) {
        return ApiResponse.success("收款流水已更新", feePaymentService.updatePayment(id, request));
    }

    @Operation(summary = "删除收款流水", description = "逻辑删除留痕；实收合计由流水现算，删除后立即从台账消失")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:fee:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        feePaymentService.deletePayment(id);
        return ApiResponse.success("收款流水已删除", null);
    }
}
