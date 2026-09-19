package com.jiaopei.platform.education.service;

import com.jiaopei.platform.common.api.PageResult;
import com.jiaopei.platform.education.domain.entity.CourseEntity;
import com.jiaopei.platform.education.dto.CourseCreateRequest;
import com.jiaopei.platform.education.dto.CoursePageQuery;
import com.jiaopei.platform.education.dto.CourseUpdateRequest;
import java.util.List;

public interface CourseService {

    List<CourseEntity> listCourses(Long institutionId, Long categoryId);

    PageResult<CourseEntity> pageCourses(CoursePageQuery query);

    CourseEntity createCourse(CourseCreateRequest request);

    CourseEntity updateCourse(Long id, CourseUpdateRequest request);

    void deleteCourse(Long id);
}
