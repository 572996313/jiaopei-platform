package com.jiaopei.platform.education.service;

import com.jiaopei.platform.common.api.PageResult;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.dto.TeacherCreateRequest;
import com.jiaopei.platform.education.dto.TeacherPageQuery;
import com.jiaopei.platform.education.dto.TeacherUpdateRequest;
import java.util.List;

public interface TeacherService {

    List<TeacherEntity> listTeachers(Long institutionId, Long campusId);

    PageResult<TeacherEntity> pageTeachers(TeacherPageQuery query);

    TeacherEntity createTeacher(TeacherCreateRequest request);

    TeacherEntity updateTeacher(Long id, TeacherUpdateRequest request);

    void deleteTeacher(Long id);
}
