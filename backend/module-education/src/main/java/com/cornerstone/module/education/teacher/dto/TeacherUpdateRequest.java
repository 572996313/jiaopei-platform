package com.cornerstone.module.education.teacher.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

@Schema(description = "教师更新请求")
public record TeacherUpdateRequest(
    @Schema(description = "校区ID")
    @JsonAlias("campus_id")
    @JsonDeserialize(using = FlexibleLongIdDeserializer.class)
    Long campusId,
    @Schema(description = "教师编号")
    @JsonAlias({"code", "teacher_code"})
    String teacherCode,
    @Schema(description = "教师姓名")
    @JsonAlias({"name", "teacher_name"})
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