package com.cornerstone.module.education.fee.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收费价目：校区/班级/学期/学员四个维度，0 表示该维度不限。
 *
 * <p>用 0 而不是 NULL 做"不限"哨兵，是因为 MySQL 唯一键里 NULL 互不相等，
 * 用 NULL 会让同一组维度能插出无穷多行（同 {@code jp_school_calendar_day.campus_id} 的口径）。
 * 四个维度列故意不建外键：0 指不到任何行。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_fee_price")
@Schema(description = "收费价目")
public class FeePriceEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "收费项目ID")
    private Long feeItemId;

    @Schema(description = "校区ID(0=不限校区)")
    private Long campusId;

    @Schema(description = "班级ID(0=不限班级)")
    private Long classId;

    @Schema(description = "学期ID(0=不限学期)")
    private Long termId;

    @Schema(description = "学员ID(0=不限学员,非0=学员级覆盖)")
    private Long studentId;

    @Schema(description = "月标准(应收=收费月数×月标准,NULL=该维度不提供)")
    private BigDecimal monthlyPrice;

    @Schema(description = "日标准(按天项目当月发生=出勤天数×日标准,NULL=该维度不提供)")
    private BigDecimal dailyPrice;

    @Schema(description = "状态(active=启用,disabled=停用)", allowableValues = {"active", "disabled"})
    private String status;

    @Schema(description = "备注")
    private String remark;
}
