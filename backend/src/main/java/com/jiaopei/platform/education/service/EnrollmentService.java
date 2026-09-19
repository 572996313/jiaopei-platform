package com.jiaopei.platform.education.service;

import com.jiaopei.platform.common.api.PageResult;
import com.jiaopei.platform.education.domain.entity.EnrollmentEntity;
import com.jiaopei.platform.education.dto.EnrollmentCreateRequest;
import com.jiaopei.platform.education.dto.EnrollmentPageQuery;
import com.jiaopei.platform.education.dto.EnrollmentView;
import java.util.List;

public interface EnrollmentService {

    List<EnrollmentEntity> listEnrollments(Long institutionId, Long classId, Long studentId);

    PageResult<EnrollmentView> pageEnrollments(EnrollmentPageQuery query);

    EnrollmentEntity createEnrollment(EnrollmentCreateRequest request);
}
