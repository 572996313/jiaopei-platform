package com.cornerstone.module.education.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "事件列表/详情视图")
public record MessageEventView(
    @Schema(description = "事件ID") Long id,
    @Schema(description = "机构ID") Long institutionId,
    @Schema(description = "校区ID") Long campusId,
    @Schema(description = "事件类型码") String typeCode,
    @Schema(description = "事件类型名称") String typeName,
    @Schema(description = "分类字典(message_category)") String category,
    @Schema(description = "级别(info/warning/critical)") String level,
    @Schema(description = "标题") String title,
    @Schema(description = "正文(纯文本快照)") String body,
    @Schema(description = "状态(系统:active/resolved；人工:open/in_progress/done/closed)") String status,
    @Schema(description = "班级锚点ID") Long classId,
    @Schema(description = "学员锚点ID") Long studentId,
    @Schema(description = "教师锚点ID") Long teacherId,
    @Schema(description = "课次锚点ID") Long lessonId,
    @Schema(description = "来源单据类型") String refType,
    @Schema(description = "来源单据ID") Long refId,
    @Schema(description = "发生/成立时间") LocalDateTime occurredAt,
    @Schema(description = "过期时间") LocalDateTime expireAt,
    @Schema(description = "指派人账号ID") Long assigneeUserId,
    @Schema(description = "指派人姓名") String assigneeName,
    @Schema(description = "截止时间") LocalDateTime dueAt,
    @Schema(description = "深链") String actionUrl,
    @Schema(description = "当前用户首次已读时间") LocalDateTime readAt,
    @Schema(description = "当前用户是否已读") boolean read,
    @Schema(description = "消解/完成时间") LocalDateTime resolvedAt,
    @Schema(description = "消解/完成人账号ID") Long resolvedBy,
    @Schema(description = "消解/完成人姓名") String resolvedByName,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "创建人账号ID") Long createdBy,
    @Schema(description = "创建人姓名") String createdByName
) {
}
