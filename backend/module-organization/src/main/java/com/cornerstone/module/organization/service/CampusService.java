package com.cornerstone.module.organization.service;

import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.dto.CampusCreateRequest;
import com.cornerstone.module.organization.dto.CampusUpdateRequest;
import java.util.List;

public interface CampusService {

    List<CampusEntity> listCampuses(Long institutionId);

    CampusEntity createCampus(CampusCreateRequest request);

    CampusEntity updateCampus(Long id, CampusUpdateRequest request);

    void deleteCampus(Long id);
}
