package com.jiaopei.platform.organization.service;

import com.jiaopei.platform.organization.domain.entity.TimeSlotEntity;
import com.jiaopei.platform.organization.dto.TimeSlotCreateRequest;
import java.util.List;

public interface TimeSlotService {

    List<TimeSlotEntity> listTimeSlots(Long institutionId);

    TimeSlotEntity createTimeSlot(TimeSlotCreateRequest request);
}
