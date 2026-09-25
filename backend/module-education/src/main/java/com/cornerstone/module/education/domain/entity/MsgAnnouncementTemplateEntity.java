package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_msg_announcement_template")
@Schema(description = "公告模板(新建公告时预填的可复用骨架)")
public class MsgAnnouncementTemplateEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离,取自建模板的人的登录态)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long institutionId;

    @Schema(description = "模板名称(仅用于区分,不强制唯一)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "预填的公告标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "预填的公告正文(套用时原样填进表单,不做变量渲染)",
        requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "公告类型字典(announcement_type:holiday=放假安排,schedule_change=教务调整,"
        + "teaching_notice=教学通知,urgent=紧急)",
        allowableValues = {"holiday", "schedule_change", "teaching_notice", "urgent"},
        requiredMode = Schema.RequiredMode.REQUIRED)
    private String typeCode;

    @Schema(description = "是否置顶(套用时带进表单)")
    private Boolean topFlag;

    @Schema(description = "预填接收对象(逗号分隔:teacher=教师,parent=学员家长,account=全部登录账号;留空=套用时再选)",
        allowableValues = {"teacher", "parent", "account"})
    private String audienceCodes;

    @Schema(description = "校区范围(all=全部校区,part=指定校区)", allowableValues = {"all", "part"})
    private String campusScope;

    @Schema(description = "指定校区ID列表(campus_scope=part时生效,逗号分隔)")
    private String campusIds;

    @Schema(description = "限定班级ID列表(逗号分隔,空=按校区全发)")
    private String classIds;

    @Schema(description = "备注(模板使用说明,只在管理端可见,不进公告正文)")
    private String remark;
}
