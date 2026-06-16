package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.StudentParentEntity;
import com.jiaopei.platform.education.dto.StudentParentCreateRequest;
import java.util.List;

public interface StudentParentService {

    List<StudentParentEntity> listStudentParents(Long institutionId, Long studentId);

    StudentParentEntity createStudentParent(StudentParentCreateRequest request);

    StudentParentEntity updateStudentParent(Long id, StudentParentCreateRequest request);

    void deleteStudentParent(Long id);
}
