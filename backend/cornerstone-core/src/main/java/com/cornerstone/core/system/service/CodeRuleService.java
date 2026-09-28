package com.cornerstone.core.system.service;

import com.cornerstone.core.system.dto.CodeRuleUpdateRequest;
import com.cornerstone.core.system.dto.CodeRuleView;
import java.util.List;

public interface CodeRuleService {

    /** 列出全部已登记资源及其规则（含"还没配置过"的资源）；启用的资源附带下一个号预览。 */
    List<CodeRuleView> listRules();

    /** 按资源码保存规则（不存在则建、存在则改）。资源码必须已在代码里登记。 */
    CodeRuleView updateRule(String resourceCode, CodeRuleUpdateRequest request);

    /**
     * 取该资源的下一个建议编号（新增页"生成"按钮走的入口）。
     *
     * <p>只读，不落库，所以取完不保存会留下一个断号——一期口径允许断号。</p>
     */
    String nextCode(String resourceCode);

    /**
     * 保存侧统一入口：操作员填了就用他填的，留空且该资源启用了规则才自动生成，
     * 留空且没启用就报"XX编号不能为空"。业务 Service 不要再各自判空生成。
     *
     * @param submitted 操作员提交的编号，允许为 null 或空白
     * @return 一定非空
     */
    String resolveForCreate(String resourceCode, String submitted);

    /**
     * 已启用自动编号的资源码清单。
     *
     * <p>给新增页决定"要不要显示那颗生成按钮"用：维护规则的权限码是系统管理页的，业务操作员没有，
     * 所以这里只能另开一个只需登录的入口，不能让前端去读全量清单。</p>
     */
    List<String> listEnabledResourceCodes();

    /**
     * 无人工提交值时的自动取号：该资源没启用规则就返回 {@code null}，不报错。
     *
     * <p>给"操作员根本不填编号"的入口用（小程序自助建档一类）。那种路径不能改走
     * {@link #resolveForCreate}——留空且未启用时它会抛"XX不能为空"，而这类入口没有"必填"可言。</p>
     */
    String autoCodeIfEnabled(String resourceCode);
}
