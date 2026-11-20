package com.cornerstone.module.education.fee.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "按考勤重算请求(台账 / 班级+学期 二选一)")
public record FeeRecalculateRequest(
    @Schema(description = "台账ID(优先;传了就只重算这一份台账)")
    @JsonAlias("ledger_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long ledgerId,
    @Schema(description = "学期ID(与班级一起用于批量重算)")
    @JsonAlias("term_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long termId,
    @Schema(description = "班级ID(与学期一起用于批量重算)")
    @JsonAlias("class_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long classId
) {
}
