package com.cornerstone.module.education.message.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 事件账本：一次发生/成立一行。
 *
 * <p>产生事件时不解析接收人；读取时按「可见范围 + 个人订阅」筛选。
 * 系统派生事件的 status 由条件是否仍成立决定；人工事项由人流转。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_msg_event")
@Schema(description = "事件账本")
public class MsgEventEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID(锚点,可空)")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long campusId;

    @Schema(description = "事件类型码")
    private String typeCode;

    @Schema(description = "分类字典(message_category)")
    private String category;

    @Schema(description = "级别(info/warning/critical)")
    private String level;

    @Schema(description = "标题(模板渲染后的纯文本快照)")
    private String title;

    @Schema(description = "正文(模板渲染后的纯文本快照)")
    private String body;

    @Schema(description = "状态(系统:active/resolved/expired;人工:open/in_progress/done/closed)")
    private String status;

    @Schema(description = "班级锚点")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long anchorClassId;

    @Schema(description = "学员锚点")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long anchorStudentId;

    @Schema(description = "教师锚点")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long anchorTeacherId;

    @Schema(description = "课次锚点")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long anchorLessonId;

    @Schema(description = "来源单据类型")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String refType;

    @Schema(description = "来源单据ID")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long refId;

    @Schema(description = "去重键(同一业务对象同一问题幂等)")
    private String dedupeKey;

    @Schema(description = "发生/成立时间")
    private LocalDateTime occurredAt;

    @Schema(description = "过期时间(空=不自动过期)")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime expireAt;

    @Schema(description = "消解/完成时间")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime resolvedAt;

    @Schema(description = "消解/完成人账号ID")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long resolvedBy;

    @Schema(description = "指派人账号ID")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long assigneeUserId;

    @Schema(description = "截止时间")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime dueAt;

    @Schema(description = "深链(渲染后按白名单校验)")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String actionUrl;
}
