package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 报名记录视图,分页列表用,已回带学员与班级名称,前端无需再全量加载学员/班级做名称回显。
 */
@Schema(description = "报名记录视图(含学员与班级名称)")
public record EnrollmentView(
    @Schema(description = "报名记录 ID") Long id,
    @Schema(description = "机构 ID(多租户隔离)") Long institutionId,
    @Schema(description = "学员 ID") Long studentId,
    @Schema(description = "学号") String studentNo,
    @Schema(description = "学员姓名") String studentName,
    @Schema(description = "班级 ID") Long classId,
    @Schema(description = "班级名称") String className,
    @Schema(description = "报名编号") String enrollmentNo,
    @Schema(description = "报名渠道(front_desk=前台报名,consultant=顾问录入,online=线上线索,referral=转介绍)") String channel,
    @Schema(description = "状态(submitted=已提交,confirmed=已确认,cancelled=已取消)") String status,
    @Schema(description = "报名日期") LocalDate enrolledAt,
    @Schema(description = "备注") String remark
) {
}
