package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_schedule_plan")
@Schema(description = "排课计划(每周节次模式,批量展开为课次批次)")
public class SchedulePlanEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID")
    private Long campusId;

    @Schema(description = "学期ID")
    private Long termId;

    @Schema(description = "班级ID(课程随班级带出,计划不单独存课程)")
    private Long classId;

    @Schema(description = "教室ID(一期固定教室,不做自动选室)")
    private Long classroomId;

    @Schema(description = "授课教师ID列表(JSON数组,展开课次时写入课次教师关系)")
    private String teacherIdsJson;

    @Schema(description = "每周节次模式(JSON数组[{weekday,timeSlotId}],weekday 1=周一,7=周日)")
    private String slotsJson;

    @Schema(description = "排课频率(every_week=每周,odd_week=单周,even_week=双周;单双周以起始日期所在自然周为第1周)",
            allowableValues = {"every_week", "odd_week", "even_week"})
    private String weekFrequency;

    @Schema(description = "生效起始日期")
    private LocalDate startDate;

    @Schema(description = "生效截止日期")
    private LocalDate endDate;

    @Schema(description = "跳过日期列表(JSON数组,ISO日期字符串,展开时整日略过)")
    private String skipDatesJson;

    @Schema(description = "计划状态(active=启用,inactive=停用;停用仅停止展开,不影响已生成课次)",
            allowableValues = {"active", "inactive"})
    private String status;

    @Schema(description = "备注")
    private String remark;
}
