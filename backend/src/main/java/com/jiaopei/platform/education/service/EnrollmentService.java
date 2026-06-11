package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.EnrollmentEntity;
import com.jiaopei.platform.education.dto.EnrollmentCreateRequest;
import java.util.List;

public interface EnrollmentService {

    List<EnrollmentEntity> listEnrollments(Long institutionId, Long classId, Long studentId);

    EnrollmentEntity createEnrollment(EnrollmentCreateRequest request);
}
