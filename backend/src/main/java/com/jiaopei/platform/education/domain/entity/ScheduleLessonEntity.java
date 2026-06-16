package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schedule_lesson")
public class ScheduleLessonEntity extends BaseEntity {

    private Long institutionId;
    private Long campusId;
    private Long termId;
    private Long classId;
    private Long classroomId;
    private Long timeSlotId;
    private LocalDate lessonDate;
    private Integer lessonWeekday;
    private LocalTime startTime;
    private LocalTime endTime;
    private String lessonTitle;
    private String lessonStatus;
    private String changeType;
    private Long relatedLessonId;
    private String remark;
}
