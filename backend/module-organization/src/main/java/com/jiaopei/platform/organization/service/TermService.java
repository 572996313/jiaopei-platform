package com.jiaopei.platform.organization.service;

import com.jiaopei.platform.organization.domain.entity.TermEntity;
import com.jiaopei.platform.organization.dto.TermCreateRequest;
import com.jiaopei.platform.organization.dto.TermUpdateRequest;
import java.util.List;

public interface TermService {

    List<TermEntity> listTerms(Long institutionId);

    TermEntity createTerm(TermCreateRequest request);

    TermEntity updateTerm(Long id, TermUpdateRequest request);

    void deleteTerm(Long id);
}
