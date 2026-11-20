package com.cornerstone.module.education.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;

@Schema(description = "收费台账更新请求(班级/学期/学员建账后不可改)")
public record FeeLedgerUpdateRequest(
    @Schema(description = "状态(active=在收,settled=已结清)", allowableValues = {"active", "settled"})
    String status,
    @Schema(description = "备注")
    String remark,
    @Schema(description = "收费项目行；按费用项目整表覆盖，未列出的项目在无收款时删除")
    @Valid
    List<FeeLedgerItemRequest> items
) {
}
