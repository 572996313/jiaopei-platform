package com.cornerstone.module.education.service;

import com.cornerstone.module.education.domain.entity.ClassTeacherEntity;
import com.cornerstone.module.education.dto.ClassTeacherCreateRequest;
import java.util.List;

public interface ClassTeacherService {

    List<ClassTeacherEntity> listClassTeachers(Long classId, Long teacherId);

    ClassTeacherEntity createClassTeacher(ClassTeacherCreateRequest request);

    ClassTeacherEntity updateClassTeacher(Long id, ClassTeacherCreateRequest request);

    void deleteClassTeacher(Long id);
}
