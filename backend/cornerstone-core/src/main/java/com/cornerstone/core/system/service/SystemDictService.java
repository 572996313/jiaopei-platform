package com.cornerstone.core.system.service;

import com.cornerstone.core.system.dto.DictItemCreateRequest;
import com.cornerstone.core.system.dto.DictItemUpdateRequest;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.dto.DictOptionGroupView;
import com.cornerstone.core.system.dto.DictTypeCreateRequest;
import com.cornerstone.core.system.dto.DictTypeUpdateRequest;
import com.cornerstone.core.system.dto.DictTypeView;
import java.util.List;

public interface SystemDictService {

    List<DictTypeView> listTypes();

    List<DictItemView> listItems(String typeCode);

    List<DictOptionGroupView> listOptions(List<String> typeCodes);

    DictTypeView createType(DictTypeCreateRequest request);

    DictTypeView updateType(Long id, DictTypeUpdateRequest request);

    void deleteType(Long id);

    DictItemView createItem(DictItemCreateRequest request);

    DictItemView updateItem(Long id, DictItemUpdateRequest request);

    void deleteItem(Long id);
}
