package com.cornerstone.module.organization.config;

import com.cornerstone.core.system.code.CodeRuleContributor;
import com.cornerstone.core.system.code.CodeRuleSpec;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 组织主数据模块的编号资源登记：校区、教室、学期、时段。
 *
 * <p>这些行业名词只登记在本模块，底座产物里不出现（守护：{@code ExternalIdVocabularyGuardTest}）。
 * 模块关闭时这几条资源在底座侧查不到，对应页面的"生成"按钮报"未登记的编码资源"，
 * 其余资源不受影响。</p>
 *
 * <p>这里给的是<b>默认格式</b>与"编号存在哪张表哪一列"。操作员在「编号规则」页改过之后，
 * 生效格式以 {@code sys_code_rule} 的行为准；没配过规则的资源即使登记了也不能取号
 * （报"未启用自动编号"），所以本文件合入不会改变任何页面的现有行为。</p>
 *
 * <p>表名列名列必须是本模块真实在写的那一列：取号要在这一列上找同号段的最大值，
 * 拼错不报错，只是永远取到 001（守护：{@code CodeRuleContributionTest}）。</p>
 */
@Component
public class OrganizationCodeRuleContributor implements CodeRuleContributor {

    @Override
    public List<CodeRuleSpec> specs() {
        return List.of(
            // 校区：一个机构就那么几个，不带日期段、序号全局累加即可
            new CodeRuleSpec("campus", "校区编码", "jp_campus", "campus_code",
                "CMP", "-", "", 3),
            // 教室：跨校区共用一条号段，机构管理员靠编号认不出校区，但客户只要"不重复、有规律"
            new CodeRuleSpec("classroom", "教室编码", "jp_classroom", "classroom_code",
                "ROM", "-", "", 3),
            // 学期：一年就那么几档，带年份段既是规律也是断点（跨年从 01 重排）
            new CodeRuleSpec("term", "学期编码", "jp_term", "term_code",
                "TRM", "-", "yyyy", 2),
            // 时段：节次模板生成出来的一行行时间，编号只是给禁排规则与课次当快捷引用
            new CodeRuleSpec("time_slot", "时段编码", "jp_time_slot", "slot_code",
                "SLOT", "-", "", 3));
    }
}
