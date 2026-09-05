package com.jiaopei.platform.system.service;

import com.jiaopei.platform.system.dto.SystemConfigCreateRequest;
import com.jiaopei.platform.system.dto.SystemConfigUpdateRequest;
import com.jiaopei.platform.system.dto.SystemConfigView;

import java.util.List;

public interface SystemConfigService {

    List<SystemConfigView> listConfigs();

    SystemConfigView getConfig(Long id);

    String getConfigValue(String key, String defaultValue);

    Boolean getBooleanConfig(String key, Boolean defaultValue);

    Integer getIntConfig(String key, Integer defaultValue);

    SystemConfigView createConfig(SystemConfigCreateRequest request);

    SystemConfigView updateConfig(Long id, SystemConfigUpdateRequest request);

    void deleteConfig(Long id);
}
