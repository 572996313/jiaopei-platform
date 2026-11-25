package com.cornerstone.core.system.lineage;

import java.util.List;

/**
 * 两个业务单据之间的关系。
 *
 * @param fromCode        来源单据编码
 * @param toCode          目标单据编码
 * @param relation        关系类型，中文，如 生成 / 汇总 / 重算 / 核销 / 取价 / 展开
 * @param linkFields      关联字段，说明两边靠什么字段对上
 * @param triggerLabel    触发入口的中文名，没有界面入口时可为 null
 * @param triggerEndpoint 触发接口，格式 {@code METHOD /path}，没有接口时可为 null
 * @param description     一句话说明这条关系怎么发生
 * @param confirmed       业务口径是否已确认；false 会在页面上标成「待确认」
 */
public record DocumentEdge(
    String fromCode,
    String toCode,
    String relation,
    List<String> linkFields,
    String triggerLabel,
    String triggerEndpoint,
    String description,
    boolean confirmed
) {
}
