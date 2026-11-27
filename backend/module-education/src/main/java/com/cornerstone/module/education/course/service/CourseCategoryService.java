package com.cornerstone.module.education.course.service;

import com.cornerstone.module.education.course.dto.CourseCategoryCreateRequest;
import com.cornerstone.module.education.course.dto.CourseCategoryUpdateRequest;
import com.cornerstone.module.education.domain.entity.CourseCategoryEntity;
import java.util.List;

public interface CourseCategoryService {

    List<CourseCategoryEntity> listCategories(Long institutionId);

    CourseCategoryEntity createCategory(CourseCategoryCreateRequest request);

    CourseCategoryEntity updateCategory(Long id, CourseCategoryUpdateRequest request);

    void deleteCategory(Long id);
}
