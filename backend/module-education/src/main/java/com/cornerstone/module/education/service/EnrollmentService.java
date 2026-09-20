package com.cornerstone.module.education.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.domain.entity.EnrollmentEntity;
import com.cornerstone.module.education.dto.EnrollmentCreateRequest;
import com.cornerstone.module.education.dto.EnrollmentPageQuery;
import com.cornerstone.module.education.dto.EnrollmentView;
import java.util.List;

public interface EnrollmentService {

    List<EnrollmentEntity> listEnrollments(Long institutionId, Long classId, Long studentId);

    PageResult<EnrollmentView> pageEnrollments(EnrollmentPageQuery query);

    EnrollmentEntity createEnrollment(EnrollmentCreateRequest request);
}
