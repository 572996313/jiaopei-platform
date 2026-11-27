package com.cornerstone.module.education.message.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 事件处理时间线：人工事项的创建/评论/指派/状态流转/补录留痕。
 *
 * <p>时间线只追加不修改，与事件状态分开存储，避免把留痕写进主表反复覆盖。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_msg_event_log")
@Schema(description = "事件处理时间线")
public class MsgEventLogEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "事件ID")
    private Long eventId;

    @Schema(description = "操作人账号ID")
    private Long userId;

    @Schema(description = "动作(create/comment/assign/status/backfill;代码判定,不入字典)")
    private String action;

    @Schema(description = "内容")
    private String content;
}
