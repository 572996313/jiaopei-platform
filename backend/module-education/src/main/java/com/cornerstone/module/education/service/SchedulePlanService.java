package com.cornerstone.module.education.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.dto.SchedulePlanCommitRequest;
import com.cornerstone.module.education.dto.SchedulePlanCommitResponse;
import com.cornerstone.module.education.dto.SchedulePlanPageQuery;
import com.cornerstone.module.education.dto.SchedulePlanPreviewResponse;
import com.cornerstone.module.education.dto.SchedulePlanRequest;
import com.cornerstone.module.education.dto.SchedulePlanView;

public interface SchedulePlanService {

    PageResult<SchedulePlanView> pagePlans(SchedulePlanPageQuery query);

    SchedulePlanView getPlan(Long id);

    SchedulePlanPreviewResponse preview(SchedulePlanRequest request);

    SchedulePlanCommitResponse commit(SchedulePlanCommitRequest request);

    SchedulePlanView updatePlan(Long id, SchedulePlanRequest request);

    void deletePlan(Long id);

    /**
     * 撤销该计划生成且未开课的课次(仅日期不早于今天、状态为已排课/已调课的课次)。
     *
     * @return 被撤销的课次数
     */
    int cancelPlanLessons(Long id);
}
