package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "公告接收人预览(发布前按范围解析,不落库)")
public record AnnouncementRecipientPreview(
    @Schema(description = "教师档命中人数(已开通登录账号的教师)") Integer teacherCount,
    @Schema(description = "学员家长档命中人数(绑定了学员账号的小程序账号)") Integer parentCount,
    @Schema(description = "全部登录账号档命中人数") Integer accountCount,
    @Schema(description = "去重后的预计触达总人数(同一账号命中多档只算一次)") Integer total,
    @Schema(description = "范围内未绑定登录账号的学员数(这些学员的家长收不到站内公告)") Integer unboundStudentCount,
    @Schema(description = "各档命中的班级/校区是否被限定,供前端解释为什么人数为 0") List<String> notices
) {
}
