package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 教师档案与登录账号的匹配候选结果。
 */
@Schema(description = "教师档案与登录账号的匹配候选结果")
public record TeacherAccountLinkCandidateResult(
    @Schema(description = "本机构未绑定账号的教师档案数") Integer unlinkedTotal,
    @Schema(description = "本机构未绑定且填了手机号的档案数") Integer matchableTotal,
    @Schema(description = "候选行数(手机号命中多个账号时会多于一位教师)") Integer candidateCount,
    @Schema(description = "候选明细") List<TeacherAccountLinkCandidateView> rows
) {
}
