package com.cornerstone.module.education.fee.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "收费台账创建请求")
public record FeeLedgerCreateRequest(
    @Schema(description = "机构ID(多租户隔离,留空取登录机构)")
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long institutionId,
    @Schema(description = "班级ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("class_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "班级不能为空")
    Long classId,
    @Schema(description = "学期ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("term_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "学期不能为空")
    Long termId,
    @Schema(description = "学员ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "学员不能为空")
    Long studentId,
    @Schema(description = "状态(active=在收,settled=已结清)", allowableValues = {"active", "settled"})
    String status,
    @Schema(description = "备注")
    String remark,
    @Schema(description = "收费项目行；留空则按当前启用的收费项目自动建行")
    @Valid
    List<FeeLedgerItemRequest> items
) {
}
