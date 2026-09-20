package com.cornerstone.module.organization.service;

import com.cornerstone.module.organization.dto.TimeSlotApplyRequest;
import com.cornerstone.module.organization.dto.TimeSlotApplyResponse;
import com.cornerstone.module.organization.dto.TimeSlotTemplateCreateRequest;
import com.cornerstone.module.organization.dto.TimeSlotTemplateUpdateRequest;
import com.cornerstone.module.organization.dto.TimeSlotTemplateView;
import java.util.List;

public interface TimeSlotTemplateService {

    List<TimeSlotTemplateView> listTemplates(Long operatorInstitutionId, Long institutionId, Long campusId);

    TimeSlotTemplateView createTemplate(Long operatorInstitutionId, TimeSlotTemplateCreateRequest request);

    TimeSlotTemplateView updateTemplate(Long operatorInstitutionId, Long id, TimeSlotTemplateUpdateRequest request);

    void deleteTemplate(Long operatorInstitutionId, Long id);

    TimeSlotApplyResponse applyTemplate(Long operatorInstitutionId, Long id, TimeSlotApplyRequest request);
}
