package com.cornerstone.module.education.course.service;

import com.cornerstone.module.education.course.dto.ClassTypeCreateRequest;
import com.cornerstone.module.education.course.dto.ClassTypeUpdateRequest;
import com.cornerstone.module.education.domain.entity.ClassTypeEntity;
import java.util.List;

public interface ClassTypeService {

    List<ClassTypeEntity> listClassTypes(Long institutionId);

    ClassTypeEntity createClassType(ClassTypeCreateRequest request);

    ClassTypeEntity updateClassType(Long id, ClassTypeUpdateRequest request);

    void deleteClassType(Long id);
}
