package com.cornerstone.module.education.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "公告模板(列表与套用一次给全,模板量小不分页)")
public record AnnouncementTemplateView(
    @Schema(description = "模板ID") Long id,
    @Schema(description = "模板名称") String name,
    @Schema(description = "预填的公告标题") String title,
    @Schema(description = "预填的公告正文") String content,
    @Schema(description = "公告类型字典值(announcement_type)") String typeCode,
    @Schema(description = "是否置顶") Boolean top,
    @Schema(description = "预填接收对象(teacher=教师,parent=学员家长,account=全部登录账号)") List<String> audienceCodes,
    @Schema(description = "校区范围(all=全部校区,part=指定校区)") String campusScope,
    @Schema(description = "指定校区ID列表") List<Long> campusIds,
    @Schema(description = "限定班级ID列表") List<Long> classIds,
    @Schema(description = "备注") String remark
) {
}
