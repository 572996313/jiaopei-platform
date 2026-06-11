package com.jiaopei.platform.organization.service;

import com.jiaopei.platform.organization.domain.entity.InstitutionEntity;
import com.jiaopei.platform.organization.dto.InstitutionCreateRequest;
import java.util.List;

public interface InstitutionService {

    List<InstitutionEntity> listInstitutions();

    InstitutionEntity createInstitution(InstitutionCreateRequest request);
}
