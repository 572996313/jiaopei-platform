package com.cornerstone.core.system.code;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 底座自己的编号资源登记：机构编码。
 *
 * <p>只登记底座持有的这一条，业务实体的资源码一律由各模块自己的 {@link CodeRuleContributor} 实现贡献，
 * 底座产物里不留行业名词（守护：{@code ExternalIdVocabularyGuardTest}）。</p>
 */
@Component
public class InstitutionCodeRuleContributor implements CodeRuleContributor {

    @Override
    public List<CodeRuleSpec> specs() {
        return List.of(new CodeRuleSpec(
            "institution", "机构编码", "sys_institution", "org_code",
            "ORG", "-", "yyyyMMdd", 3));
    }
}
