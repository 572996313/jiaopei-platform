package com.jiaopei.platform.organization.service;

import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.dto.CampusCreateRequest;
import com.jiaopei.platform.organization.dto.CampusUpdateRequest;
import java.util.List;

public interface CampusService {

    List<CampusEntity> listCampuses(Long institutionId);

    CampusEntity createCampus(CampusCreateRequest request);

    CampusEntity updateCampus(Long id, CampusUpdateRequest request);

    void deleteCampus(Long id);
}
