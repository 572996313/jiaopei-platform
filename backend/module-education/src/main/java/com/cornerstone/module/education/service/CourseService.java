package com.cornerstone.module.education.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.domain.entity.CourseEntity;
import com.cornerstone.module.education.dto.CourseCreateRequest;
import com.cornerstone.module.education.dto.CoursePageQuery;
import com.cornerstone.module.education.dto.CourseUpdateRequest;
import java.util.List;

public interface CourseService {

    List<CourseEntity> listCourses(Long institutionId, Long categoryId);

    PageResult<CourseEntity> pageCourses(CoursePageQuery query);

    CourseEntity createCourse(CourseCreateRequest request);

    CourseEntity updateCourse(Long id, CourseUpdateRequest request);

    void deleteCourse(Long id);
}
