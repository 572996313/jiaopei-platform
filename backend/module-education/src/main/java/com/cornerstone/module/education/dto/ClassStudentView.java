package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 班级学员关系视图,分页列表用,已回带班级与学员名称,前端无需再全量加载班级/学员做名称回显。
 */
@Schema(description = "班级学员关系视图(含班级与学员名称)")
public record ClassStudentView(
    @Schema(description = "班级学员关系 ID") Long id,
    @Schema(description = "机构 ID(多租户隔离)") Long institutionId,
    @Schema(description = "班级 ID") Long classId,
    @Schema(description = "班级编号") String classCode,
    @Schema(description = "班级名称") String className,
    @Schema(description = "课程 ID") Long courseId,
    @Schema(description = "班型 ID") Long classTypeId,
    @Schema(description = "学员 ID") Long studentId,
    @Schema(description = "学号") String studentNo,
    @Schema(description = "学员姓名") String studentName,
    @Schema(description = "状态(active=在读,quit=退班,graduated=结业,reserved=预占)",
            allowableValues = {"active", "quit", "graduated", "reserved"}) String status,
    @Schema(description = "加入日期") LocalDate joinedAt,
    @Schema(description = "离开日期") LocalDate leftAt,
    @Schema(description = "备注") String remark
) {
}
