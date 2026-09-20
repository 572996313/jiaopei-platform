package com.cornerstone.module.education.service;

import com.cornerstone.module.education.domain.entity.CourseCategoryEntity;
import com.cornerstone.module.education.dto.CourseCategoryCreateRequest;
import com.cornerstone.module.education.dto.CourseCategoryUpdateRequest;
import java.util.List;

public interface CourseCategoryService {

    List<CourseCategoryEntity> listCategories(Long institutionId);

    CourseCategoryEntity createCategory(CourseCategoryCreateRequest request);

    CourseCategoryEntity updateCategory(Long id, CourseCategoryUpdateRequest request);

    void deleteCategory(Long id);
}
