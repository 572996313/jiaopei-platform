package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.CourseCategoryEntity;
import com.jiaopei.platform.education.dto.CourseCategoryCreateRequest;
import java.util.List;

public interface CourseCategoryService {

    List<CourseCategoryEntity> listCategories(Long institutionId);

    CourseCategoryEntity createCategory(CourseCategoryCreateRequest request);
}
