package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.ClassTypeEntity;
import com.jiaopei.platform.education.dto.ClassTypeCreateRequest;
import com.jiaopei.platform.education.dto.ClassTypeUpdateRequest;
import java.util.List;

public interface ClassTypeService {

    List<ClassTypeEntity> listClassTypes(Long institutionId);

    ClassTypeEntity createClassType(ClassTypeCreateRequest request);

    ClassTypeEntity updateClassType(Long id, ClassTypeUpdateRequest request);

    void deleteClassType(Long id);
}
