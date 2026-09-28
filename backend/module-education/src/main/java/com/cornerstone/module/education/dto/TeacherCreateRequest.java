package com.cornerstone.module.education.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "教师创建请求")
public record TeacherCreateRequest(
    @Schema(description = "机构ID(多租户隔离)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("institution_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    @NotNull(message = "机构ID不能为空")
    Long institutionId,
    @Schema(description = "校区ID")
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @Schema(description = "教师编号(可留空,留空时按编号规则自动生成)")
    @JsonAlias({"code", "teacher_code"})
    // 可留空：留空时由编号规则补号，判空在 Service 里做
    String teacherCode,
    @Schema(description = "教师姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias({"name", "teacher_name"})
    @NotBlank(message = "教师姓名不能为空")
    String teacherName,
    @Schema(description = "联系电话")
    @JsonAlias({"mobile", "teacher_phone"})
    String phone,
    @Schema(description = "性别字典(gender:male=男,female=女,unknown=未知)", allowableValues = {"male", "female", "unknown"})
    String gender,
    @Schema(description = "职称(如 初级/中级/高级)")
    String title,
    @Schema(description = "用工类型字典(employment_type:full_time=全职,part_time=兼职,contract=外聘)", allowableValues = {"full_time", "part_time", "contract"})
    @JsonAlias("employment_type")
    String employmentType,
    @Schema(description = "周课时上限(排课超出只提示不拦;留空=不限)")
    @JsonAlias("max_weekly_lessons")
    @Min(value = 1, message = "周课时上限必须大于0")
    Integer maxWeeklyLessons,
    @Schema(description = "每日授课节数上限(排课超出只提示不拦;留空=不限)")
    @JsonAlias("max_daily_lessons")
    @Min(value = 1, message = "每日授课节数上限必须大于0")
    Integer maxDailyLessons,
    @Schema(description = "教师状态字典(teacher_status:active=在职,suspended=暂停,left=离职)", allowableValues = {"active", "suspended", "left"})
    String status,
    @Schema(description = "入职日期")
    @JsonAlias("hired_at")
    LocalDate hiredAt,
    @Schema(description = "备注")
    String remark
) {
}
