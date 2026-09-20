package com.cornerstone.core.system.service;

import com.cornerstone.core.system.dto.ListConfigCreateRequest;
import com.cornerstone.core.system.dto.ListConfigUpdateRequest;
import com.cornerstone.core.system.dto.ListConfigView;

import java.util.List;

public interface ListConfigService {

    List<ListConfigView> listConfigs();

    /**
     * 按资源编码取启用的列表配置,供通用列表组件消费;未配置或已停用返回 null。
     */
    ListConfigView getByResourceCode(String resourceCode);

    ListConfigView createConfig(ListConfigCreateRequest request);

    ListConfigView updateConfig(Long id, ListConfigUpdateRequest request);

    void deleteConfig(Long id);
}
