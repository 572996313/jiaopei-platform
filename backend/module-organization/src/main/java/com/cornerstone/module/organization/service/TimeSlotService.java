package com.cornerstone.module.organization.service;

import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.dto.TimeSlotCreateRequest;
import com.cornerstone.module.organization.dto.TimeSlotUpdateRequest;
import java.util.List;

public interface TimeSlotService {

    /** campusId 为空时返回该机构全部时间段；传了则只返回本校区的加上机构通用（campus_id 为空）的 */
    List<TimeSlotEntity> listTimeSlots(Long operatorInstitutionId, Long institutionId, Long campusId);

    TimeSlotEntity createTimeSlot(Long operatorInstitutionId, TimeSlotCreateRequest request);

    TimeSlotEntity updateTimeSlot(Long operatorInstitutionId, Long id, TimeSlotUpdateRequest request);

    void deleteTimeSlot(Long operatorInstitutionId, Long id);
}
