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
}
