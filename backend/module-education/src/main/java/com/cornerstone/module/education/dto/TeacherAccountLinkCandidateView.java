package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 按手机号匹配账号的一行候选：一行 = 一位未绑定账号的教师档案（手机号命中多个账号时拆成多行）。
 *
 * @param actionable 服务端愿意按这一行执行绑定（命中且未被占用的账号才为 true），false 的行只用于解释"为什么没匹配上"
 * @param nameMatched 教师姓名与账号显示名称是否一致，作为人工确认的默认勾选依据
 */
@Schema(description = "教师档案与登录账号的匹配候选")
public record TeacherAccountLinkCandidateView(
    @Schema(description = "教师档案ID") Long teacherId,
    @Schema(description = "教师编码") String teacherCode,
    @Schema(description = "教师姓名") String teacherName,
    @Schema(description = "教师档案上的手机号") String phone,
    @Schema(description = "候选登录账号ID,未命中为 null") Long userId,
    @Schema(description = "候选登录账号") String username,
    @Schema(description = "账号显示名称") String displayName,
    @Schema(description = "账号状态(active=启用,disabled=停用)") String accountStatus,
    @Schema(description = "姓名与显示名称是否一致;未命中账号为 null") Boolean nameMatched,
    @Schema(description = "这一行能不能执行绑定") boolean actionable,
    @Schema(description = "不能绑定或多账号命中时的说明;无说明为 null") String notice
) {
}
