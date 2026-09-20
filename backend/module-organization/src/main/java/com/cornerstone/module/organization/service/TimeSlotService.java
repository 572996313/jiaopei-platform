package com.cornerstone.module.organization.service;

import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.dto.TimeSlotCreateRequest;
import com.cornerstone.module.organization.dto.TimeSlotUpdateRequest;
import java.util.List;

public interface TimeSlotService {

    List<TimeSlotEntity> listTimeSlots(Long operatorInstitutionId, Long institutionId);

    TimeSlotEntity createTimeSlot(Long operatorInstitutionId, TimeSlotCreateRequest request);

    TimeSlotEntity updateTimeSlot(Long operatorInstitutionId, Long id, TimeSlotUpdateRequest request);

    void deleteTimeSlot(Long operatorInstitutionId, Long id);
}
