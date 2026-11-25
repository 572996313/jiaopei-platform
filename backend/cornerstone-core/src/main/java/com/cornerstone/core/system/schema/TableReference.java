package com.cornerstone.core.system.schema;

/**
 * 一条数据库外键：子表的一列引用父表的一列。
 *
 * <p>外键表达的是「引用」关系（单据挂在哪些主数据上），不等于业务上的「生成」关系；
 * 生成关系仍由各模块的 {@code DocumentLineageContributor} 声明。</p>
 *
 * @param childTable     子表
 * @param childColumn    子表列
 * @param parentTable    父表
 * @param parentColumn   父表列
 * @param constraintName 外键约束名
 */
public record TableReference(
    String childTable,
    String childColumn,
    String parentTable,
    String parentColumn,
    String constraintName
) {
}
