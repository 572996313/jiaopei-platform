package com.cornerstone.module.education.service;

import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.dto.ClassStudentCreateRequest;
import java.util.List;

public interface ClassStudentService {

    List<ClassStudentEntity> listClassStudents(Long classId, Long studentId);

    ClassStudentEntity createClassStudent(ClassStudentCreateRequest request);

    void deleteClassStudent(Long id);
}
