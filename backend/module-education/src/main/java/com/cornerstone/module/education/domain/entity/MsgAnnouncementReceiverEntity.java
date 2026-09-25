package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_msg_announcement_receiver")
@Schema(description = "公告接收人(发布时按范围解析展开,一行一人)")
public class MsgAnnouncementReceiverEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离,冗余自主档)")
    private Long institutionId;

    @Schema(description = "公告ID(jp_msg_announcement.id)")
    private Long announcementId;

    @Schema(description = "接收人账号ID(sys_user_account.id)")
    private Long userId;

    @Schema(description = "该接收人因哪一档范围命中(teacher=教师,parent=学员家长,account=全部登录账号)",
        allowableValues = {"teacher", "parent", "account"})
    private String audienceCode;

    @Schema(description = "已读时间(未读为NULL;重复打开只记首次)")
    private LocalDateTime readAt;
}
