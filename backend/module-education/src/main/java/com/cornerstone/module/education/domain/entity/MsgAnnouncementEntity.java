package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_msg_announcement")
@Schema(description = "公告(人工撰写、按范围广播、可撤回的站内公告)")
public class MsgAnnouncementEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离,取自发布人登录态)")
    private Long institutionId;

    @Schema(description = "公告标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "正文(发布时冻结的文本快照,落库后不随班级改名等主档变更漂移)",
        requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "公告类型字典(announcement_type:holiday=放假安排,schedule_change=教务调整,"
        + "teaching_notice=教学通知,urgent=紧急)",
        allowableValues = {"holiday", "schedule_change", "teaching_notice", "urgent"},
        requiredMode = Schema.RequiredMode.REQUIRED)
    private String typeCode;

    @Schema(description = "公告状态(draft=草稿,published=已发布,recalled=已撤回;由发布/撤回动作判定,不入字典)",
        allowableValues = {"draft", "published", "recalled"})
    private String status;

    @Schema(description = "是否置顶(置顶在接收端排最前,撤回即取消)")
    private Boolean topFlag;

    @Schema(description = "接收对象(逗号分隔:teacher=教师,parent=学员家长,account=全部登录账号)",
        allowableValues = {"teacher", "parent", "account"})
    private String audienceCodes;

    @Schema(description = "校区范围(all=全部校区,part=指定校区)", allowableValues = {"all", "part"})
    private String campusScope;

    @Schema(description = "指定校区ID列表(campus_scope=part时生效,逗号分隔)")
    private String campusIds;

    @Schema(description = "限定班级ID列表(逗号分隔,空=按校区全发)")
    private String classIds;

    @Schema(description = "接收人总数(发布时解析出的范围快照,阅读进度的固定分母)")
    private Integer receiverTotal;

    @Schema(description = "发布时间(未发布为NULL)")
    private LocalDateTime publishedAt;

    @Schema(description = "发布人账号ID")
    private Long publishedBy;

    @Schema(description = "撤回时间(未撤回为NULL)")
    private LocalDateTime recalledAt;

    @Schema(description = "撤回人账号ID")
    private Long recalledBy;

    @Schema(description = "撤回原因(操作留痕,列表行内展示)")
    private String recallReason;

    @Schema(description = "备注")
    private String remark;
}
