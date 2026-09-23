package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_enrollment")
@Schema(description = "报名记录")
public class EnrollmentEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "学员ID")
    private Long studentId;

    @Schema(description = "班级ID")
    private Long classId;

    @Schema(description = "报名编号")
    private String enrollmentNo;

    @Schema(description = "报名渠道字典(enrollment_channel:front_desk=前台报名,consultant=顾问录入,online=线上线索,referral=转介绍)")
    private String channel;

    @Schema(description = "状态(PENDING=待审核,APPROVED=已通过,REJECTED=已驳回,CANCELLED=已取消)")
    private String status;

    @Schema(description = "报名日期")
    private LocalDate enrolledAt;

    @Schema(description = "备注")
    private String remark;
}
