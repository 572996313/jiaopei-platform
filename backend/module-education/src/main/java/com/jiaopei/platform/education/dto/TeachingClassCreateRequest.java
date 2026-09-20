package com.jiaopei.platform.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jiaopei.platform.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "教学班级创建请求")
public record TeachingClassCreateRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @Schema(description = "校区ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "校区ID不能为空")
    Long campusId,
    @Schema(description = "学期ID")
    @JsonAlias("term_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long termId,
    @Schema(description = "课程ID")
    @JsonAlias("course_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long courseId,
    @Schema(description = "班级类型ID")
    @JsonAlias("class_type_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long classTypeId,
    @Schema(description = "班级编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"code", "class_code"})
    @NotBlank(message = "班级编号不能为空")
    String classCode,
    @Schema(description = "班级名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "class_name"})
    @NotBlank(message = "班级名称不能为空")
    String className,
    @Schema(description = "主教师ID")
    @JsonAlias({"primary_teacher_id", "teacher_id"})
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long teacherId,
    @Schema(description = "容量上限")
    @JsonAlias({"capacity", "capacity_limit"})
    @Min(value = 0, message = "容量上限必须大于或等于0")
    Integer capacityLimit,
    @Schema(description = "当前人数(服务端按在班学员关系统计，请求值不生效)")
    @JsonAlias("current_students")
    @Min(value = 0, message = "当前人数必须大于或等于0")
    Integer currentStudents,
    @Schema(description = "状态(planning=规划中,active=启用,inactive=停用,closed=已结课;不传默认规划中)", allowableValues = {"planning", "active", "inactive", "closed"})
    String status,
    @Schema(description = "开班日期")
    @JsonAlias("opened_at")
    LocalDate openedAt,
    @Schema(description = "备注")
    String remark
) {
}
