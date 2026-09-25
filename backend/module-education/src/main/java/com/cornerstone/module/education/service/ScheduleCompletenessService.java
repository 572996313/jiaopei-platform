package com.cornerstone.module.education.service;

import com.cornerstone.module.education.dto.ScheduleCompletenessQuery;
import com.cornerstone.module.education.dto.ScheduleCompletenessResponse;

public interface ScheduleCompletenessService {

    /**
     * 按班级×自然周汇总排课完成度。
     *
     * @param operatorInstitutionId 登录用户所属机构，用于把查询参数里的机构 ID 锁死在本机构内
     */
    ScheduleCompletenessResponse report(Long operatorInstitutionId, ScheduleCompletenessQuery query);
}
