package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class_student")
@Schema(description = "班级学员关系")
public class ClassStudentEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "班级ID")
    private Long classId;

    @Schema(description = "学员ID")
    private Long studentId;

    @Schema(description = "状态(ACTIVE=在读,QUIT=退班,GRADUATED=结业,RESERVED=预占)")
    private String status;

    @Schema(description = "加入日期")
    private LocalDate joinedAt;

    @Schema(description = "离开日期")
    private LocalDate leftAt;

    @Schema(description = "备注")
    private String remark;
}
