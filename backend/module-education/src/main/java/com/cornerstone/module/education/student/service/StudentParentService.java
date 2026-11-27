package com.cornerstone.module.education.student.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.domain.entity.StudentParentEntity;
import com.cornerstone.module.education.student.dto.StudentParentCreateRequest;
import com.cornerstone.module.education.student.dto.StudentParentPageQuery;
import com.cornerstone.module.education.student.dto.StudentParentView;
import java.util.List;

public interface StudentParentService {

    List<StudentParentEntity> listStudentParents(Long institutionId, Long studentId);

    PageResult<StudentParentView> pageStudentParents(StudentParentPageQuery query);

    StudentParentEntity createStudentParent(StudentParentCreateRequest request);

    StudentParentEntity updateStudentParent(Long id, StudentParentCreateRequest request);

    void deleteStudentParent(Long id);
}
