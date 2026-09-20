package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
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

    @Schema(description = "当前人数")
    private Integer currentStudents;

    @Schema(description = "状态(planning=规划中,active=启用,inactive=停用,closed=已结课)", allowableValues = {"planning", "active", "inactive", "closed"})
    private String status;

    @Schema(description = "开班日期")
    private LocalDate openedAt;

    @Schema(description = "备注")
    private String remark;
}
