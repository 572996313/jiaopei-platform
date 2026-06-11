package com.jiaopei.platform.organization.service;

import com.jiaopei.platform.organization.domain.entity.ClassroomEntity;
import com.jiaopei.platform.organization.dto.ClassroomCreateRequest;
import java.util.List;

public interface ClassroomService {

    List<ClassroomEntity> listClassrooms(Long campusId);

    ClassroomEntity createClassroom(ClassroomCreateRequest request);
}
