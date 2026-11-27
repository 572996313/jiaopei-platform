package com.cornerstone.module.education.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "我收到的公告(接收端列表项/详情)")
public record MyAnnouncementView(
    @Schema(description = "公告ID") Long id,
    @Schema(description = "公告标题") String title,
    @Schema(description = "正文(发布时冻结的文本快照)") String content,
    @Schema(description = "公告类型字典值(announcement_type)") String typeCode,
    @Schema(description = "是否置顶") Boolean top,
    @Schema(description = "发布时间") LocalDateTime publishedAt,
    @Schema(description = "我已读时间(未读为NULL)") LocalDateTime readAt,
    @Schema(description = "我因哪一档范围收到(teacher=教师,parent=学员家长,account=全部登录账号)") String audienceCode
) {
}
