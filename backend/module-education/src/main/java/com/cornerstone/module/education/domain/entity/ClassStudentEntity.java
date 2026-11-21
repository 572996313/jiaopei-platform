package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
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

    @Schema(description = "状态(active=在读,quit=退班,graduated=结业,reserved=预占)")
    private String status;

    @Schema(description = "报名渠道字典(enrollment_channel:front_desk=前台报名,consultant=顾问录入,online=线上线索,referral=转介绍)")
    private String channel;

    @Schema(description = "加入日期")
    private LocalDate joinedAt;

    @Schema(description = "离开日期")
    private LocalDate leftAt;

    @Schema(description = "备注")
    private String remark;
}
