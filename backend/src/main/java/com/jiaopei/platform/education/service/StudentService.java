package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.StudentEntity;
import com.jiaopei.platform.education.dto.StudentCreateRequest;
import com.jiaopei.platform.education.dto.StudentUpdateRequest;
import java.util.List;

public interface StudentService {

    List<StudentEntity> listStudents(Long institutionId, Long campusId, String keyword);

    StudentEntity createStudent(StudentCreateRequest request);

    StudentEntity updateStudent(Long id, StudentUpdateRequest request);

    void deleteStudent(Long id);
}
