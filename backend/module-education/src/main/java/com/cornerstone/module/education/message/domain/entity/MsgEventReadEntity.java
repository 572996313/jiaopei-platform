package com.cornerstone.module.education.message.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 事件已读：按人一行，一个人读了不影响别人。
 *
 * <p>唯一键 (user_id, event_id, deleted) 保证重复打开只记首次时间。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_msg_event_read")
@Schema(description = "事件已读")
public class MsgEventReadEntity extends BaseEntity {

    @Schema(description = "账号ID")
    private Long userId;

    @Schema(description = "事件ID")
    private Long eventId;

    @Schema(description = "首次已读时间")
    private LocalDateTime readAt;
}
