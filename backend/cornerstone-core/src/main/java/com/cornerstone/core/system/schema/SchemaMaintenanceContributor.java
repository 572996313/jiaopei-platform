package com.cornerstone.core.system.schema;

import java.util.List;

/**
 * 数据字典「维护入口」的代码侧登记（SPI）。
 *
 * <p>底座只定义接口与合并逻辑，具体表→页面的映射由各模块在自己的 {@code config} 包实现并注册为 Bean。
 * 模块关闭时入口随之消失，字典页自然显示"没有登记维护入口"；底座不 import 任何业务包。</p>
 *
 * <p>同一张表被多个 Contributor 登记时先登记者生效，避免重复跳转。</p>
 */
public interface SchemaMaintenanceContributor {

    /** 模块标识，仅用于排障，如 core / education / organization */
    String moduleKey();

    List<SchemaMaintenanceEntry> entries();
}
