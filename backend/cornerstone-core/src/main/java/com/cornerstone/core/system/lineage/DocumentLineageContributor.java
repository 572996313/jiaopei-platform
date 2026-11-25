package com.cornerstone.core.system.lineage;

import java.util.List;

/**
 * 业务单据链路的代码侧登记入口（SPI）。
 *
 * <p>底座只定义接口与合并逻辑，具体单据与关系由各模块在自己的 {@code config} 包实现并注册为 Bean。
 * 模块关闭时实现随之消失，页面自然少掉该段；底座不 import 任何业务包。</p>
 *
 * <p>声明的是业务关系（谁由谁生成、汇总或核销），不是某一次请求的实际数据流；
 * 表只表示该单据落在哪张表，不区分本次读了还是写了。关系尚未拍板时把 {@code confirmed} 置为 false，
 * 页面会显式标成「待确认」，避免把草案当成事实。</p>
 */
public interface DocumentLineageContributor {

    /** 模块标识，仅用于分组与排障，如 core / education / organization */
    String moduleKey();

    /** 本模块登记的单据节点 */
    List<DocumentNode> documents();

    /** 本模块登记的单据关系；两端必须是已登记的单据编码 */
    List<DocumentEdge> edges();
}
