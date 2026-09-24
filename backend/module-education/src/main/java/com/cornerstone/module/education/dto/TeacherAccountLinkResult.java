package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 教师档案绑定登录账号的结果。任一无效配对会整批拒绝，所以这里只有成功数。
 */
@Schema(description = "教师档案绑定登录账号结果")
public record TeacherAccountLinkResult(
    @Schema(description = "本次完成绑定的档案数") Integer linkedCount
) {
}
