package com.cornerstone.module.education.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.dto.TeacherCreateRequest;
import com.cornerstone.module.education.dto.TeacherPageQuery;
import com.cornerstone.module.education.dto.TeacherUpdateRequest;
import java.util.List;

public interface TeacherService {

    List<TeacherEntity> listTeachers(Long institutionId, Long campusId);

    PageResult<TeacherEntity> pageTeachers(TeacherPageQuery query);

    TeacherEntity createTeacher(TeacherCreateRequest request);

    TeacherEntity updateTeacher(Long id, TeacherUpdateRequest request);

    void deleteTeacher(Long id);
}
