package com.jiaopei.platform.tenant.service;

import com.jiaopei.platform.tenant.domain.InstitutionEntity;
import com.jiaopei.platform.tenant.dto.CurrentInstitutionUpdateRequest;
import com.jiaopei.platform.tenant.dto.CurrentInstitutionView;
import com.jiaopei.platform.tenant.dto.InstitutionCreateRequest;
import java.util.List;

public interface InstitutionService {

    List<InstitutionEntity> listInstitutions();

    InstitutionEntity createInstitution(InstitutionCreateRequest request);

    CurrentInstitutionView getCurrentInstitution(Long institutionId);

    CurrentInstitutionView updateCurrentInstitution(Long institutionId, CurrentInstitutionUpdateRequest request);
}
