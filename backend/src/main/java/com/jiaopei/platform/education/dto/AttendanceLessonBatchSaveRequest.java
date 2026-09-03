package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "课次批量出勤保存请求")
public record AttendanceLessonBatchSaveRequest(
    @Schema(description = "课次ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("lesson_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "lessonId is required")
    Long lessonId,
    @Schema(description = "出勤明细列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "items is required")
    List<@Valid AttendanceBatchItemRequest> items
) {
}
