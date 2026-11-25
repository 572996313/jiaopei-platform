package com.cornerstone.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "第三方登录账号选择列表")
public record AccountSelectionView(
    @Schema(description = "渠道编码") String providerCode,
    @Schema(description = "可选择的账号列表") List<AccountOptionView> accounts
) {
}
