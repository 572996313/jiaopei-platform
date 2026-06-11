package com.jiaopei.platform.organization.service;

import com.jiaopei.platform.organization.domain.entity.TermEntity;
import com.jiaopei.platform.organization.dto.TermCreateRequest;
import java.util.List;

public interface TermService {

    List<TermEntity> listTerms(Long institutionId);

    TermEntity createTerm(TermCreateRequest request);
}
