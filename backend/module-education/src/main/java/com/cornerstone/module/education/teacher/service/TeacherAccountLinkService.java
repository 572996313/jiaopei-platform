package com.cornerstone.module.education.teacher.service;

import com.cornerstone.module.education.teacher.dto.TeacherAccountLinkCandidateResult;
import com.cornerstone.module.education.teacher.dto.TeacherAccountLinkRequest;
import com.cornerstone.module.education.teacher.dto.TeacherAccountLinkResult;

/**
 * 教师档案与登录账号的关联。账号本身不在这里建：开号走底座的
 * {@code /api/system/user-import}，本服务只负责把两边的手机号对上并写关联列。
 */
public interface TeacherAccountLinkService {

    /** 按手机号列出可绑定的候选，不写库 */
    TeacherAccountLinkCandidateResult previewCandidates();

    /** 按前端确认的配对执行绑定，任一无效配对整批拒绝 */
    TeacherAccountLinkResult link(TeacherAccountLinkRequest request);
}
