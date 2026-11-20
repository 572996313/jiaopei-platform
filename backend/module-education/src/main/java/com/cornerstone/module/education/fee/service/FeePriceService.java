package com.cornerstone.module.education.fee.service;

import com.cornerstone.module.education.fee.domain.entity.FeePriceEntity;
import com.cornerstone.module.education.fee.dto.FeePriceCreateRequest;
import com.cornerstone.module.education.fee.dto.FeePriceUpdateRequest;
import java.util.List;

/** 收费价目维护。 */
public interface FeePriceService {

    List<FeePriceEntity> listPrices(Long institutionId, Long feeItemId, Long campusId);

    FeePriceEntity createPrice(FeePriceCreateRequest request);

    FeePriceEntity updatePrice(Long id, FeePriceUpdateRequest request);

    void deletePrice(Long id);
}
