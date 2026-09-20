package com.cornerstone.module.education.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.dto.StudentCreateRequest;
import com.cornerstone.module.education.dto.StudentPageQuery;
import com.cornerstone.module.education.dto.StudentUpdateRequest;
import java.util.List;

public interface StudentService {

    List<StudentEntity> listStudents(Long institutionId, Long campusId, String keyword);

    PageResult<StudentEntity> pageStudents(StudentPageQuery query);

    StudentEntity createStudent(StudentCreateRequest request);

    StudentEntity updateStudent(Long id, StudentUpdateRequest request);

    void deleteStudent(Long id);
}
