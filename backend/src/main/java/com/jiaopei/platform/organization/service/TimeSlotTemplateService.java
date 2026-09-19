package com.jiaopei.platform.organization.service;

import com.jiaopei.platform.organization.dto.TimeSlotApplyRequest;
import com.jiaopei.platform.organization.dto.TimeSlotApplyResponse;
import com.jiaopei.platform.organization.dto.TimeSlotTemplateCreateRequest;
import com.jiaopei.platform.organization.dto.TimeSlotTemplateUpdateRequest;
import com.jiaopei.platform.organization.dto.TimeSlotTemplateView;
import java.util.List;

public interface TimeSlotTemplateService {

    List<TimeSlotTemplateView> listTemplates(Long operatorInstitutionId, Long institutionId, Long campusId);

    TimeSlotTemplateView createTemplate(Long operatorInstitutionId, TimeSlotTemplateCreateRequest request);

    TimeSlotTemplateView updateTemplate(Long operatorInstitutionId, Long id, TimeSlotTemplateUpdateRequest request);

    void deleteTemplate(Long operatorInstitutionId, Long id);

    TimeSlotApplyResponse applyTemplate(Long operatorInstitutionId, Long id, TimeSlotApplyRequest request);
}
