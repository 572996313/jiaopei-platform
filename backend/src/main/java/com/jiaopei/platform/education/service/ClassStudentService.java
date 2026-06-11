package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.ClassStudentEntity;
import com.jiaopei.platform.education.dto.ClassStudentCreateRequest;
import java.util.List;

public interface ClassStudentService {

    List<ClassStudentEntity> listClassStudents(Long classId, Long studentId);

    ClassStudentEntity createClassStudent(ClassStudentCreateRequest request);
}
