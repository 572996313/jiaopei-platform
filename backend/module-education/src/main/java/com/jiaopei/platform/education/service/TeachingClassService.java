package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.TeachingClassCreateRequest;
import java.util.List;

public interface TeachingClassService {

    List<TeachingClassEntity> listClasses(Long institutionId, Long campusId, Long termId);

    TeachingClassEntity createClass(TeachingClassCreateRequest request);

    TeachingClassEntity updateClass(Long id, TeachingClassCreateRequest request);

    void deleteClass(Long id);
}
