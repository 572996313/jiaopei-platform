package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_schedule_rule")
@Schema(description = "排课规则(班级/教师的星期×节次禁排必排约束)")
public class ScheduleRuleEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "规则类型(forbidden=禁排,required=必排)", allowableValues = {"forbidden", "required"})
    private String ruleType;

    @Schema(description = "规则对象类型(class=班级,teacher=教师,classroom=教室)", allowableValues = {"class", "teacher", "classroom"})
    private String targetType;

    @Schema(description = "规则对象ID(target_type=class时为班级ID,=teacher时为教师ID)")
    private Long targetId;

    @Schema(description = "每周节次格子(JSON数组[{weekday,timeSlotId}],weekday 1=周一,7=周日)")
    private String slotsJson;

    @Schema(description = "生效起始日期(空=不限,按课次日期判定)")
    private LocalDate startDate;

    @Schema(description = "生效截止日期(空=不限)")
    private LocalDate endDate;

    @Schema(description = "规则状态(active=启用,inactive=停用;停用不参与校验)", allowableValues = {"active", "inactive"})
    private String status;

    @Schema(description = "批注(为什么禁排/必排,校验提示里带出)")
    private String remark;
}
