package com.cornerstone.core.system.lineage;

import java.util.List;

/**
 * 一个业务单据节点。
 *
 * @param code          稳定编码，建议带模块前缀，如 {@code education.fee_ledger}
 * @param name          中文名，面向业务与实施
 * @param primaryTable  主表名，必须与迁移脚本/数据字典一致
 * @param relatedTables 附属表（明细、关系表等），没有时传空列表
 * @param pagePath      管理端路由，没有独立页面时可为 null
 * @param permissionCode 进入该页面的权限码，可为 null
 * @param description   一句话说明这张单据是什么
 * @param confirmed     业务口径是否已确认；false 会在页面上标成「待确认」
 * @param remark        补充说明，如未落地的字段、待拍板的规则
 */
public record DocumentNode(
    String code,
    String name,
    String primaryTable,
    List<String> relatedTables,
    String pagePath,
    String permissionCode,
    String description,
    boolean confirmed,
    String remark
) {
}
