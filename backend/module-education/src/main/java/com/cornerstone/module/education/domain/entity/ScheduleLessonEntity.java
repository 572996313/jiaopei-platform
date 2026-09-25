package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schedule_lesson")
@Schema(description = "排课课次")
public class ScheduleLessonEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID")
    private Long campusId;

    @Schema(description = "学期ID")
    private Long termId;

    @Schema(description = "班级ID")
    private Long classId;

    @Schema(description = "教室ID")
    private Long classroomId;

    @Schema(description = "时段ID")
    private Long timeSlotId;

    @Schema(description = "来源排课计划ID(手工排课为空)")
    private Long planId;

    @Schema(description = "上课日期")
    private LocalDate lessonDate;

    @Schema(description = "星期(1=周一,7=周日)")
    private Integer lessonWeekday;

    @Schema(description = "开始时间")
    private LocalTime startTime;

    @Schema(description = "结束时间")
    private LocalTime endTime;

    @Schema(description = "课次标题")
    private String lessonTitle;

    @Schema(description = "课次状态(scheduled=已排课,rescheduled=已调课,cancelled=已停课,makeup=已补课,completed=已结课)", allowableValues = {"scheduled", "rescheduled", "cancelled", "makeup", "completed"})
    private String lessonStatus;

    @Schema(description = "变更类型(cancelled_makeup=停课补课)", allowableValues = {"cancelled_makeup"})
    private String changeType;

    @Schema(description = "关联课次ID(变更/补课指向原课次)")
    private Long relatedLessonId;

    @Schema(description = "备注")
    private String remark;
}
