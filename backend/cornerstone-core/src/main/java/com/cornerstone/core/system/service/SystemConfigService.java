package com.cornerstone.core.system.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.system.dto.SystemConfigCreateRequest;
import com.cornerstone.core.system.dto.SystemConfigPageQuery;
import com.cornerstone.core.system.dto.SystemConfigUpdateRequest;
import com.cornerstone.core.system.dto.SystemConfigView;

import java.util.List;

public interface SystemConfigService {

    List<SystemConfigView> listConfigs();

    PageResult<SystemConfigView> pageConfigs(SystemConfigPageQuery query);

    SystemConfigView getConfig(Long id);

    String getConfigValue(String key, String defaultValue);

    Boolean getBooleanConfig(String key, Boolean defaultValue);

    Integer getIntConfig(String key, Integer defaultValue);

    SystemConfigView createConfig(SystemConfigCreateRequest request);

    SystemConfigView updateConfig(Long id, SystemConfigUpdateRequest request);

    void deleteConfig(Long id);
}
