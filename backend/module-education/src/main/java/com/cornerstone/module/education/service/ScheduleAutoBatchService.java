package com.cornerstone.module.education.service;

import com.cornerstone.module.education.dto.ScheduleAutoBatchRequest;
import com.cornerstone.module.education.dto.ScheduleAutoBatchView;
import com.cornerstone.module.education.dto.ScheduleAutoPlanResponse;
import com.cornerstone.module.education.dto.ScheduleAutoRollbackResponse;
import java.util.List;

/**
 * 自动排课（按缺口补排）：把完成度报表算出的"这个班这周还差几节"补进课表。
 *
 * <p>一期只有补排这一个模式，不新建计划、不改计划：教师与教室沿用该班既有计划。
 * 预览与执行共用同一份决策结果，执行阶段不再算第二遍，避免"预览看到的"和"落库落下的"不一致。</p>
 */
public interface ScheduleAutoBatchService {

    /** 只出草案不落库：返回准备排的每一格，以及补不上的班级/周次与原因。 */
    ScheduleAutoPlanResponse preview(ScheduleAutoBatchRequest request);

    /** 按预览同一套决策落库。一格都没排上时不建批次，清单里不会堆一串空批次。 */
    ScheduleAutoPlanResponse run(ScheduleAutoBatchRequest request);

    /** 本机构（可选校区）最近的补排批次，按执行时间倒序，最多 20 条。 */
    List<ScheduleAutoBatchView> listBatches(Long institutionId, Long campusId);

    /** 整批撤销：只删本批次里"还没上的课"，已点名/已停课的一律不动。 */
    ScheduleAutoRollbackResponse rollback(Long id);
}
