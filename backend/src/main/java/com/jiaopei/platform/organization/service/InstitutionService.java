package com.jiaopei.platform.organization.service;

import com.jiaopei.platform.organization.domain.entity.InstitutionEntity;
import com.jiaopei.platform.organization.dto.CurrentInstitutionUpdateRequest;
import com.jiaopei.platform.organization.dto.CurrentInstitutionView;
import com.jiaopei.platform.organization.dto.InstitutionCreateRequest;
import java.util.List;

public interface InstitutionService {

    List<InstitutionEntity> listInstitutions();

    InstitutionEntity createInstitution(InstitutionCreateRequest request);

    CurrentInstitutionView getCurrentInstitution(Long institutionId);

    CurrentInstitutionView updateCurrentInstitution(Long institutionId, CurrentInstitutionUpdateRequest request);
}
