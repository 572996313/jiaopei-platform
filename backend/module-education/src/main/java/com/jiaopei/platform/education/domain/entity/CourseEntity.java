package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_course")
@Schema(description = "课程")
public class CourseEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "课程分类ID")
    private Long categoryId;

    @Schema(description = "课程编码")
    private String courseCode;

    @Schema(description = "课程名称")
    private String courseName;

    @Schema(description = "学科名称(如 数学/英语)")
    private String subjectName;

    @Schema(description = "授课方式(ONLINE/OFFLINE/HYBRID)")
    private String teachingMethod;

    @Schema(description = "状态(ENABLED=启用,DISABLED=停用)")
    private String status;

    @Schema(description = "备注")
    private String remark;
}
