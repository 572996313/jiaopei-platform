package com.jiaopei.platform.system.service;

import com.jiaopei.platform.system.dto.DictItemCreateRequest;
import com.jiaopei.platform.system.dto.DictItemUpdateRequest;
import com.jiaopei.platform.system.dto.DictItemView;
import com.jiaopei.platform.system.dto.DictOptionGroupView;
import com.jiaopei.platform.system.dto.DictTypeCreateRequest;
import com.jiaopei.platform.system.dto.DictTypeUpdateRequest;
import com.jiaopei.platform.system.dto.DictTypeView;
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
