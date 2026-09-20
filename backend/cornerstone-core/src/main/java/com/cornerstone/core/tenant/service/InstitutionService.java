package com.cornerstone.core.tenant.service;

import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.dto.CurrentInstitutionUpdateRequest;
import com.cornerstone.core.tenant.dto.CurrentInstitutionView;
import com.cornerstone.core.tenant.dto.InstitutionCreateRequest;
import java.util.List;

public interface InstitutionService {

    List<InstitutionEntity> listInstitutions();

    InstitutionEntity createInstitution(InstitutionCreateRequest request);

    CurrentInstitutionView getCurrentInstitution(Long institutionId);

    CurrentInstitutionView updateCurrentInstitution(Long institutionId, CurrentInstitutionUpdateRequest request);
}
