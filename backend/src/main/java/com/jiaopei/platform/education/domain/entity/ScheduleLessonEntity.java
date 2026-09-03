package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
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

    @Schema(description = "课次状态(SCHEDULED=已排课,TAKEN=已上课,CANCELLED=已取消,MAKEUP=已补课)", allowableValues = {"SCHEDULED", "TAKEN", "CANCELLED", "MAKEUP"})
    private String lessonStatus;

    @Schema(description = "变更类型(STOP=停课,MAKEUP=补课,ADJUST=调整,RESCHEDULE=改期)", allowableValues = {"STOP", "MAKEUP", "ADJUST", "RESCHEDULE"})
    private String changeType;

    @Schema(description = "关联课次ID(变更/补课指向原课次)")
    private Long relatedLessonId;

    @Schema(description = "备注")
    private String remark;
}
