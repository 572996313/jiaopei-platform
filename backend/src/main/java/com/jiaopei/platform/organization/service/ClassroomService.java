package com.jiaopei.platform.organization.service;

import com.jiaopei.platform.organization.domain.entity.ClassroomEntity;
import com.jiaopei.platform.organization.dto.ClassroomCreateRequest;
import com.jiaopei.platform.organization.dto.ClassroomUpdateRequest;
import java.util.List;

public interface ClassroomService {

    List<ClassroomEntity> listClassrooms(Long campusId);

    ClassroomEntity createClassroom(ClassroomCreateRequest request);

    ClassroomEntity updateClassroom(Long id, ClassroomUpdateRequest request);

    void deleteClassroom(Long id);
}
