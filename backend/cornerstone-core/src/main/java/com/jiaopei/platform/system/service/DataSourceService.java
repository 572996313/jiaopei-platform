package com.jiaopei.platform.system.service;

import com.jiaopei.platform.system.dto.DataSourceCreateRequest;
import com.jiaopei.platform.system.dto.DataSourceDefinitionView;
import com.jiaopei.platform.system.dto.DataSourceUpdateRequest;
import com.jiaopei.platform.system.dto.DataSourceView;
import java.util.List;
import java.util.Map;

public interface DataSourceService {

    List<DataSourceView> listDataSources();

    /** 按编码批量取启用的数据源定义(未启用/不存在/停用的编码不返回)。 */
    Map<String, DataSourceDefinitionView> getDefinitions(List<String> codes);

    /**
     * 按编码取单条启用的数据源定义,未配置或已停用返回 null。
     * 结果按编码缓存,定义变更通过 @CacheEvict 即时失效。
     */
    DataSourceDefinitionView getDefinition(String code);

    DataSourceView createDataSource(DataSourceCreateRequest request);

    DataSourceView updateDataSource(Long id, DataSourceUpdateRequest request);

    void deleteDataSource(Long id);
}