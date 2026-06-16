package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.CourseEntity;
import com.jiaopei.platform.education.dto.CourseCreateRequest;
import java.util.List;

public interface CourseService {

    List<CourseEntity> listCourses(Long institutionId, Long categoryId);

    CourseEntity createCourse(CourseCreateRequest request);
}
