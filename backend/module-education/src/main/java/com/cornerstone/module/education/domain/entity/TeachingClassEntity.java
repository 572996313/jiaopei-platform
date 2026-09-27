package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class")
@Schema(description = "教学班级")
public class TeachingClassEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID")
    private Long campusId;

    @Schema(description = "学期ID")
    private Long termId;

    @Schema(description = "课程ID")
    private Long courseId;

    @Schema(description = "班级类型ID")
    private Long classTypeId;

    @Schema(description = "班级编号")
    private String classCode;

    @Schema(description = "班级名称")
    private String className;

    @Schema(description = "主教师ID")
    private Long teacherId;

    @Schema(description = "容量上限")
    private Integer capacityLimit;

    @Schema(description = "每周应上节数(自动排课与课时完成度的目标值,空=未设定)")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)   // 编辑时留空即撤销目标；updateById 默认忽略 null，不设 ALWAYS 会清不掉
    private Integer weeklyLessons;

    @Schema(description = "每周应上节数的录入口径(weekly=按周填,monthly=按月填由系统换算)",
            allowableValues = {"weekly", "monthly"})
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String weeklyLessonsMode;

    @Schema(description = "每周应上节数的录制原值(mode=monthly 时存的是每月节数,换算结果在 weeklyLessons)")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer weeklyLessonsInput;

    @Schema(description = "整班总课时(合同/教学计划的总节数,空=未设定;只用于课时进度,不进完成度分母)")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer totalLessons;

    @Schema(description = "当前人数")
    private Integer currentStudents;

    @Schema(description = "状态(planning=规划中,active=启用,inactive=停用,closed=已结课)", allowableValues = {"planning", "active", "inactive", "closed"})
    private String status;

    @Schema(description = "开班日期")
    private LocalDate openedAt;

    @Schema(description = "备注")
    private String remark;
}
