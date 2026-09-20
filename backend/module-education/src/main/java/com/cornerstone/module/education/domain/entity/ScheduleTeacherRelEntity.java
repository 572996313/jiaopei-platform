package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schedule_teacher_rel")
@Schema(description = "课次教师关系")
public class ScheduleTeacherRelEntity extends BaseEntity {

    @Schema(description = "课次ID")
    private Long lessonId;

    @Schema(description = "教师ID")
    private Long teacherId;
}
