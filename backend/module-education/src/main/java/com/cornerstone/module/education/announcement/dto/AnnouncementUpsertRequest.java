package com.cornerstone.module.education.announcement.dto;

import com.cornerstone.core.common.web.FlexibleLongIdDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 公告新建/草稿保存请求。
 *
 * <p>刻意不含 institutionId：公告的归属机构只取发布人登录态，少一个参数就少一条绕过租户比对的通道
 * （与 SystemUserImportController 同口径）。草稿阶段允许接收对象与校区范围为空，发布时才强制校验。</p>
 */
@Schema(description = "公告新建/草稿保存请求")
public record AnnouncementUpsertRequest(
    @Schema(description = "公告标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 120, message = "公告标题不能超过 120 个字符")
    String title,

    @Schema(description = "公告正文(发布时冻结的文本快照)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "公告正文不能为空")
    String content,

    @Schema(description = "公告类型字典(announcement_type:holiday=放假安排,schedule_change=教务调整,"
        + "teaching_notice=教学通知,urgent=紧急)",
        allowableValues = {"holiday", "schedule_change", "teaching_notice", "urgent"},
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "公告类型不能为空")
    @JsonAlias("type_code")
    String typeCode,

    @Schema(description = "接收对象(teacher=教师,parent=学员家长,account=全部登录账号),草稿可为空",
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

    @Schema(description = "备注")
    String remark
) {
}
