package com.cornerstone.module.organization.service;

import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.dto.ClassroomCreateRequest;
import com.cornerstone.module.organization.dto.ClassroomUpdateRequest;
import java.util.List;

public interface ClassroomService {

    List<ClassroomEntity> listClassrooms(Long campusId);

    ClassroomEntity createClassroom(ClassroomCreateRequest request);

    ClassroomEntity updateClassroom(Long id, ClassroomUpdateRequest request);

    void deleteClassroom(Long id);
}
