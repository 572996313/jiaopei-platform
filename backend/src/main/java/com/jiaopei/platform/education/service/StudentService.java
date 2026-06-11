package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.StudentEntity;
import com.jiaopei.platform.education.dto.StudentCreateRequest;
import java.util.List;

public interface StudentService {

    List<StudentEntity> listStudents(Long institutionId, Long campusId);

    StudentEntity createStudent(StudentCreateRequest request);
}
