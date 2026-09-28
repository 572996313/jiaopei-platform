package com.cornerstone.core.system.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 到"编号实际所在的那张业务表"里取号。
 *
 * <p>底座不 import 任何业务 mapper，所以这里按表名/列名动态查。两条名字<b>只能</b>来自
 * {@link com.cornerstone.core.system.code.CodeRuleRegistry} 里启动期登记过的常量
 * （形态由 {@code CodePattern.requireSqlIdentifier} 把住），请求参数只提供资源码，
 * 永远拿不到拼接位。</p>
 *
 * <p>拼接位一律包反引号：登记的列名万一撞上 MySQL 保留字（SEPARATOR 就是），不加引号会直接 1064。
 * 白名单形态本身不收反引号，所以这层引号不会变成注入口。</p>
 *
 * <p>两条语句都是原生 SQL，不走 MyBatis-Plus 的逻辑删除拦截：判"这个号被占了没有"必须把
 * 软删墓碑算进去。业务表的编码唯一键大多不含 deleted（历史账，见 V72 的收口形态），
 * 一条墓碑就能永久占住一个号；查号时避开墓碑，比让客户拿到一个存不进去的号要好。</p>
 *
 * <p>{@code selectLastCodeBySegment} 的 LIKE 走的是"机构列在前、编码列在后"那把唯一键的
 * 非前缀位，用不上 range scan，实为覆盖索引扫描（只读索引、不回表）。万级表毫秒级，
 * 但它是"每次点一下生成按钮"的固定成本：真跑到十万级觉得慢，给编码列补一条普通索引即可，
 * 不要改成缓存或预取号段——那会把"允许断号"这个前提悄悄换掉。</p>
 */
public interface CodeSequenceMapper {

    /**
     * 取同一日期段内形态最长、字典序最大的那个编号，作为序号续排的依据。
     * 没有同段编号时返回 null。
     */
    @Select("""
        SELECT `${columnName}` FROM `${tableName}`
        WHERE `${columnName}` LIKE #{likePattern}
        ORDER BY LENGTH(`${columnName}`) DESC, `${columnName}` DESC
        LIMIT 1
        """)
    String selectLastCodeBySegment(@Param("tableName") String tableName,
                                   @Param("columnName") String columnName,
                                   @Param("likePattern") String likePattern);

    /** 该编号是否已被占用（含软删墓碑）。命中返回 1，未命中返回 null。 */
    @Select("SELECT 1 FROM `${tableName}` WHERE `${columnName}` = #{code} LIMIT 1")
    Integer selectCodeOccupied(@Param("tableName") String tableName,
                               @Param("columnName") String columnName,
                               @Param("code") String code);
}
