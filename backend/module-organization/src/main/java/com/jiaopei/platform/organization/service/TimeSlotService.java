package com.jiaopei.platform.organization.service;

import com.jiaopei.platform.organization.domain.entity.TimeSlotEntity;
import com.jiaopei.platform.organization.dto.TimeSlotCreateRequest;
import com.jiaopei.platform.organization.dto.TimeSlotUpdateRequest;
import java.util.List;

public interface TimeSlotService {

    List<TimeSlotEntity> listTimeSlots(Long operatorInstitutionId, Long institutionId);

    TimeSlotEntity createTimeSlot(Long operatorInstitutionId, TimeSlotCreateRequest request);

    TimeSlotEntity updateTimeSlot(Long operatorInstitutionId, Long id, TimeSlotUpdateRequest request);

    void deleteTimeSlot(Long operatorInstitutionId, Long id);
}
