package com.cornerstone.module.education.fee.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "按班级名册批量建账请求")
public record FeeLedgerGenerateRequest(
    @Schema(description = "机构ID(多租户隔离,留空取登录机构)")
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long institutionId,
    @Schema(description = "学期ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("term_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "学期不能为空")
    Long termId,
    @Schema(description = "班级ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("class_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "班级不能为空")
    Long classId
) {
}
