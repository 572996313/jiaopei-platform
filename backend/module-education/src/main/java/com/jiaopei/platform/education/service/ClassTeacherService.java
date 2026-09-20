package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.ClassTeacherEntity;
import com.jiaopei.platform.education.dto.ClassTeacherCreateRequest;
import java.util.List;

public interface ClassTeacherService {

    List<ClassTeacherEntity> listClassTeachers(Long classId, Long teacherId);

    ClassTeacherEntity createClassTeacher(ClassTeacherCreateRequest request);

    ClassTeacherEntity updateClassTeacher(Long id, ClassTeacherCreateRequest request);

    void deleteClassTeacher(Long id);
}
