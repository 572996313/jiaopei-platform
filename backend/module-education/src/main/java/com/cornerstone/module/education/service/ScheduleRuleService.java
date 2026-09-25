package com.cornerstone.module.education.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.dto.ScheduleRulePageQuery;
import com.cornerstone.module.education.dto.ScheduleRuleRequest;
import com.cornerstone.module.education.dto.ScheduleRuleView;

public interface ScheduleRuleService {

    PageResult<ScheduleRuleView> pageRules(ScheduleRulePageQuery query);

    ScheduleRuleView getRule(Long id);

    ScheduleRuleView createRule(ScheduleRuleRequest request);

    ScheduleRuleView updateRule(Long id, ScheduleRuleRequest request);

    void deleteRule(Long id);
}
