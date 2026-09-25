package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "公告列表/详情视图")
public record AnnouncementView(
    @Schema(description = "公告ID") Long id,
    @Schema(description = "公告标题") String title,
    @Schema(description = "正文(列表页给摘要、详情页给全文)") String content,
    @Schema(description = "公告类型字典值(announcement_type)") String typeCode,
    @Schema(description = "公告状态(draft=草稿,published=已发布,recalled=已撤回)") String status,
    @Schema(description = "是否置顶") Boolean top,
    @Schema(description = "接收对象(teacher=教师,parent=学员家长,account=全部登录账号)") List<String> audienceCodes,
    @Schema(description = "校区范围(all=全部校区,part=指定校区)") String campusScope,
    @Schema(description = "指定校区ID列表") List<Long> campusIds,
    @Schema(description = "限定班级ID列表") List<Long> classIds,
    @Schema(description = "接收人总数(发布时冻结的分母,草稿为0)") Integer receiverTotal,
    @Schema(description = "已读人数") Integer readCount,
    @Schema(description = "发布时间") LocalDateTime publishedAt,
    @Schema(description = "发布人姓名") String publishedByName,
    @Schema(description = "撤回时间") LocalDateTime recalledAt,
    @Schema(description = "撤回人姓名") String recalledByName,
    @Schema(description = "撤回原因") String recallReason,
    @Schema(description = "备注") String remark,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {
}
