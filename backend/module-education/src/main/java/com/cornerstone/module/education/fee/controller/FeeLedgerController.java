package com.cornerstone.module.education.fee.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.fee.dto.FeeLedgerCreateRequest;
import com.cornerstone.module.education.fee.dto.FeeLedgerDetailView;
import com.cornerstone.module.education.fee.dto.FeeLedgerGenerateRequest;
import com.cornerstone.module.education.fee.dto.FeeLedgerGenerateResult;
import com.cornerstone.module.education.fee.dto.FeeLedgerPageQuery;
import com.cornerstone.module.education.fee.dto.FeeLedgerRowView;
import com.cornerstone.module.education.fee.dto.FeeLedgerUpdateRequest;
import com.cornerstone.module.education.fee.service.FeeLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/education/fee-ledgers")
@Tag(name = "收费管理")
public class FeeLedgerController {

    private static final String XLSX_MEDIA_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final FeeLedgerService feeLedgerService;

    public FeeLedgerController(FeeLedgerService feeLedgerService) {
        this.feeLedgerService = feeLedgerService;
    }

    @Operation(summary = "收费台账分页列表", description = "按学期/班级/学员/关键字筛选；应收、实收、结余由服务端现算")
    @GetMapping("/page")
    @RequiresPermission("education:read")
    public ApiResponse<PageResult<FeeLedgerRowView>> page(@Valid @ParameterObject FeeLedgerPageQuery query) {
        return ApiResponse.success("收费台账分页列表已加载", feeLedgerService.pageLedgers(query));
    }

    @Operation(summary = "收费台账详情", description = "含明细行、逐月发生额、月度出勤与全部收款流水")
    @GetMapping("/{id}")
    @RequiresPermission("education:read")
    public ApiResponse<FeeLedgerDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success("收费台账已加载", feeLedgerService.getLedger(id));
    }

    @Operation(summary = "新建收费台账", description = "收费项目行留空时按当前启用项目自动建行并按价目表取价")
    @PostMapping
    @RequiresPermission("education:fee:write")
    public ApiResponse<FeeLedgerDetailView> create(@Valid @RequestBody FeeLedgerCreateRequest request) {
        return ApiResponse.success("收费台账已创建", feeLedgerService.createLedger(request));
    }

    @Operation(summary = "修改收费台账", description = "班级/学期/学员建账后不可改；收费项目按整表覆盖")
    @PutMapping("/{id}")
    @RequiresPermission("education:fee:write")
    public ApiResponse<FeeLedgerDetailView> update(@PathVariable Long id,
                                                   @Valid @RequestBody FeeLedgerUpdateRequest request) {
        return ApiResponse.success("收费台账已更新", feeLedgerService.updateLedger(id, request));
    }

    @Operation(summary = "删除收费台账", description = "已有收款流水时拒绝删除，避免财务凭证失去归属")
    @DeleteMapping("/{id}")
    @RequiresPermission("education:fee:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        feeLedgerService.deleteLedger(id);
        return ApiResponse.success("收费台账已删除", null);
    }

    @Operation(summary = "按班级名册批量建账", description = "为名册内每个在读学员建一份台账，已有台账的跳过")
    @PostMapping("/generate")
    @RequiresPermission("education:fee:write")
    public ApiResponse<FeeLedgerGenerateResult> generate(
        @Valid @RequestBody FeeLedgerGenerateRequest request) {
        return ApiResponse.success("批量建账完成", feeLedgerService.generate(request));
    }

    @Operation(summary = "重新取价并重算", description = "按当前价目表覆盖明细行的标准与来源；已手工覆盖的逐月发生额不动")
    @PostMapping("/{id}/resolve-prices")
    @RequiresPermission("education:fee:write")
    public ApiResponse<FeeLedgerDetailView> resolvePrices(@PathVariable Long id) {
        return ApiResponse.success("已按价目表重新取价", feeLedgerService.resolvePrices(id));
    }

    @Operation(summary = "导出收费出勤表(3 个 sheet)",
            description = "拆成家长情况 / 收费内容 / 出勤明细三张表，各自带班级与单位行、小计与总计")
    @GetMapping("/export/matrix")
    @RequiresPermission("education:read")
    public ResponseEntity<byte[]> exportMatrix(@Valid @ParameterObject FeeLedgerPageQuery query) {
        return xlsx(feeLedgerService.exportMatrix(query), "收费出勤表.xlsx");
    }

    @Operation(summary = "导出台账明细(一维)", description = "一个工作簿两张 sheet：台账明细(每学员每项目一行) / 逐月发生")
    @GetMapping("/export/detail")
    @RequiresPermission("education:read")
    public ResponseEntity<byte[]> exportDetail(@Valid @ParameterObject FeeLedgerPageQuery query) {
        return xlsx(feeLedgerService.exportDetail(query), "收费台账明细.xlsx");
    }

    private ResponseEntity<byte[]> xlsx(byte[] workbook, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
            .body(workbook);
    }
}
