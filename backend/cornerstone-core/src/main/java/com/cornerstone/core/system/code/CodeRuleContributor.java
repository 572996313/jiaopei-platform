package com.cornerstone.core.system.code;

import java.util.List;

/**
 * 编号资源登记：哪个模块有"需要给人填编号"的主数据，就在自己模块里登记一条。
 *
 * <p>底座不认得任何行业实体，也不该有一张写着"学员""教师"的清单——那些名字属于哪个行业，
 * 登记就该放在哪个模块（与 {@code ExternalIdFieldLabelContributor} 同一套分工）。底座只负责
 * 收集登记项、按格式取号，以及把没登记的资源码拒掉。</p>
 *
 * <p>实现类放自己模块的 config 包，随模块开关一起装配；模块没装配时它的资源在底座侧查不到，
 * 对应页面的"生成"按钮直接报"未登记的编码资源"，不会影响其他资源。</p>
 */
public interface CodeRuleContributor {

    /** 本模块登记的编号资源。 */
    List<CodeRuleSpec> specs();
}
