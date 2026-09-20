package com.cornerstone.module.organization.service;

import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.dto.TermCreateRequest;
import com.cornerstone.module.organization.dto.TermUpdateRequest;
import java.util.List;

public interface TermService {

    List<TermEntity> listTerms(Long institutionId);

    TermEntity createTerm(TermCreateRequest request);

    TermEntity updateTerm(Long id, TermUpdateRequest request);

    void deleteTerm(Long id);
}
