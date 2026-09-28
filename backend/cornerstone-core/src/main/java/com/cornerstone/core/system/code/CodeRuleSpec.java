package com.cornerstone.core.system.code;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 一个资源的编号登记：这个资源的编号叫什么、存在哪张表哪一列、默认长什么格式。
 *
 * <p>由 {@link CodeRuleContributor} 在代码里给出，<b>不入库</b>。入库的那半（{@code sys_code_rule}）
 * 只存操作员可改的格式与开关；表名列名一旦进了库，等于让运维把生成器指向任意一张表，
 * 而这两条名字在 SQL 里是拼接而非绑定参数（能拼的只有启动期登记过的常量，见 {@link CodeRuleRegistry}）。</p>
 *
 * @param resourceCode  资源码，全库唯一，与规则行的 resource_code 一一对应
 * @param label         中文展示名，同时用于报错文案（"XX编号不能为空"），行业名词只许出现在登记方模块里
 * @param tableName     编号所在表名
 * @param columnName    编号所在列名
 * @param prefix        默认前缀
 * @param separator     默认分隔符，空串表示各段直接相连
 * @param datePattern   默认日期段格式，空串表示不带日期段
 * @param serialLength  默认序号位数
 */
@Schema(description = "编号资源登记信息")
public record CodeRuleSpec(
    String resourceCode,
    String label,
    String tableName,
    String columnName,
    String prefix,
    String separator,
    String datePattern,
    int serialLength
) {
}
