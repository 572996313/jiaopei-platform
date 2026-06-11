package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.dto.TeacherCreateRequest;
import java.util.List;

public interface TeacherService {

    List<TeacherEntity> listTeachers(Long institutionId, Long campusId);

    TeacherEntity createTeacher(TeacherCreateRequest request);
}
