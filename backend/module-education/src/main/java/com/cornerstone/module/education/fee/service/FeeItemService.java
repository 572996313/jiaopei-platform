package com.cornerstone.module.education.fee.service;

import com.cornerstone.module.education.fee.domain.entity.FeeItemEntity;
import com.cornerstone.module.education.fee.dto.FeeItemCreateRequest;
import com.cornerstone.module.education.fee.dto.FeeItemUpdateRequest;
import java.util.List;

/** 收费项目维护。 */
public interface FeeItemService {

    List<FeeItemEntity> listItems(Long institutionId, String status);

    FeeItemEntity createItem(FeeItemCreateRequest request);

    FeeItemEntity updateItem(Long id, FeeItemUpdateRequest request);

    void deleteItem(Long id);
}
