package com.cornerstone.module.education.service;

import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.TeachingClassCreateRequest;
import java.util.List;

public interface TeachingClassService {

    List<TeachingClassEntity> listClasses(Long institutionId, Long campusId, Long termId);

    TeachingClassEntity createClass(TeachingClassCreateRequest request);

    TeachingClassEntity updateClass(Long id, TeachingClassCreateRequest request);

    void deleteClass(Long id);
}
