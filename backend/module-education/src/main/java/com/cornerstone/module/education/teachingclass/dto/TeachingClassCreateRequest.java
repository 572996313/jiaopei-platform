package com.cornerstone.module.education.teachingclass.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
    // 可留空：新增时留空由编号规则补号（判空在 createClass），编辑时仍必填（updateClass 里 requireNonBlank）
    String classCode,
    @Schema(description = "班级名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "class_name"})
    @NotBlank(message = "班级名称不能为空")
    String className,
    @Schema(description = "主讲教师ID(写入时同步生成/升级班级教师主讲关系;编辑时留空表示撤销主讲)")
    @JsonAlias({"primary_teacher_id", "teacher_id"})
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long teacherId,
    @Schema(description = "容量上限")
    @JsonAlias({"capacity", "capacity_limit"})
    @Min(value = 0, message = "容量上限必须大于或等于0")
    Integer capacityLimit,
    @Schema(description = "每周应上节数(自动排课与课时完成度的目标值;留空表示未设定。"
        + "口径为 monthly 时本字段由服务端按 weeklyLessonsInput 换算，请求值不生效)")
    @JsonAlias("weekly_lessons")
    @Min(value = 1, message = "每周应上节数必须大于0")
    Integer weeklyLessons,
    @Schema(description = "每周应上节数的录入口径(weekly=按周填,monthly=按月填由系统换算;不传按 weekly)",
        allowableValues = {"weekly", "monthly"})
    @JsonAlias("weekly_lessons_mode")
    String weeklyLessonsMode,
    @Schema(description = "每周应上节数的录制原值(weekly 口径下等价于 weeklyLessons;monthly 口径下是每月节数)")
    @JsonAlias("weekly_lessons_input")
    @Min(value = 1, message = "课时目标原值必须大于0")
    Integer weeklyLessonsInput,
    @Schema(description = "整班总课时(合同/教学计划的总节数;留空表示未设定，只用于课时进度，不影响完成度分母)")
    @JsonAlias("total_lessons")
    @Min(value = 1, message = "整班总课时必须大于0")
    Integer totalLessons,
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
