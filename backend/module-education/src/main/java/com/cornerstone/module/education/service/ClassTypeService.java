package com.cornerstone.module.education.service;

import com.cornerstone.module.education.domain.entity.ClassTypeEntity;
import com.cornerstone.module.education.dto.ClassTypeCreateRequest;
import com.cornerstone.module.education.dto.ClassTypeUpdateRequest;
import java.util.List;

public interface ClassTypeService {

    List<ClassTypeEntity> listClassTypes(Long institutionId);

    ClassTypeEntity createClassType(ClassTypeCreateRequest request);

    ClassTypeEntity updateClassType(Long id, ClassTypeUpdateRequest request);

    void deleteClassType(Long id);
}
