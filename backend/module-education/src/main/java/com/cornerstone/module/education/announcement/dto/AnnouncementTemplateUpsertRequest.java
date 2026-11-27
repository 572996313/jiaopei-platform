package com.cornerstone.module.education.announcement.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 公告模板新建/修改请求。
 *
 * <p>范围字段的校验规则与公告草稿完全一致（同一套 normalizeRange），差别只在于模板必须填标题与正文：
 * 空正文的模板套出来还是要重写一遍，那就不是省时间的东西。</p>
 * <p>同样刻意不含 institutionId：模板归属只取建它的人的登录态。</p>
 */
@Schema(description = "公告模板新建/修改请求")
public record AnnouncementTemplateUpsertRequest(
    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 60, message = "模板名称不能超过 60 个字符")
    String name,

    @Schema(description = "预填的公告标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 120, message = "公告标题不能超过 120 个字符")
    String title,

    @Schema(description = "预填的公告正文", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "公告正文不能为空")
    @Size(max = 2000, message = "公告正文不能超过 2000 个字符")
    String content,

    @Schema(description = "公告类型字典(announcement_type:holiday=放假安排,schedule_change=教务调整,"
        + "teaching_notice=教学通知,urgent=紧急)",
        allowableValues = {"holiday", "schedule_change", "teaching_notice", "urgent"},
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "公告类型不能为空")
    @JsonAlias("type_code")
    String typeCode,

    @Schema(description = "预填接收对象(teacher=教师,parent=学员家长,account=全部登录账号),留空=套用时再选",
        allowableValues = {"teacher", "parent", "account"})
    @JsonAlias("audience_codes")
    List<String> audienceCodes,

    @Schema(description = "校区范围(all=全部校区,part=指定校区),留空按 all", allowableValues = {"all", "part"})
    @JsonAlias("campus_scope")
    String campusScope,

    @Schema(description = "指定校区ID列表(campus_scope=part 时生效)")
    @JsonAlias("campus_ids")
    @JsonDeserialize(contentUsing = FlexibleLongIdDeserializer.class)
    List<Long> campusIds,

    @Schema(description = "限定班级ID列表,留空=按校区全发")
    @JsonAlias("class_ids")
    @JsonDeserialize(contentUsing = FlexibleLongIdDeserializer.class)
    List<Long> classIds,

    @Schema(description = "是否置顶")
    Boolean top,

    @Schema(description = "备注(模板使用说明,不进公告正文)")
    String remark
) {
}
