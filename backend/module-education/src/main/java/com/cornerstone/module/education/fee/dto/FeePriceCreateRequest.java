package com.cornerstone.module.education.fee.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "收费价目创建请求(四个维度 0 或留空=不限)")
public record FeePriceCreateRequest(
    @Schema(description = "机构ID(多租户隔离,留空取登录机构)")
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long institutionId,
    @Schema(description = "收费项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("fee_item_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "收费项目不能为空")
    Long feeItemId,
    @Schema(description = "校区ID(0=不限校区)")
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @Schema(description = "班级ID(0=不限班级)")
    @JsonAlias("class_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long classId,
    @Schema(description = "学期ID(0=不限学期)")
    @JsonAlias("term_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long termId,
    @Schema(description = "学员ID(0=不限学员,非0=学员级覆盖)")
    @JsonAlias("student_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long studentId,
    @Schema(description = "月标准")
    @JsonAlias("monthly_price")
    java.math.BigDecimal monthlyPrice,
    @Schema(description = "日标准")
    @JsonAlias("daily_price")
    java.math.BigDecimal dailyPrice,
    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    String status,
    @Schema(description = "备注")
    String remark
) {

    /** 四维哨兵：null 一律归 0，保证唯一键口径与库里一致。 */
    public long campusOrAny() {
        return campusId == null ? 0L : campusId;
    }

    public long classOrAny() {
        return classId == null ? 0L : classId;
    }

    public long termOrAny() {
        return termId == null ? 0L : termId;
    }

    public long studentOrAny() {
        return studentId == null ? 0L : studentId;
    }
}
