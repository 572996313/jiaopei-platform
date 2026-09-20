package com.jiaopei.platform.organization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "时段模板创建请求")
public record TimeSlotTemplateCreateRequest(
    @Schema(description = "机构ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @Schema(description = "校区ID,空表示机构级模板")
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "template_name"})
    @NotBlank(message = "模板名称不能为空")
    String templateName,
    @Schema(description = "节次参数快照(课程时长/课间/各时段节数/大课间/套用星期)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("config_json")
    @NotNull(message = "节次参数不能为空")
    JsonNode config,
    @Schema(description = "备注")
    String remark
) {
}
