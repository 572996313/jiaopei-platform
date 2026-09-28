package com.cornerstone.core.system.code;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 编号资源注册表：启动期把各模块登记的资源收成一张只读表，之后按资源码取。
 *
 * <p>这张表同时是 SQL 拼接的唯一白名单——{@code CodeSequenceMapper} 里的表名/列名只可能来自这里，
 * 而这里的每条都经过 {@link CodePattern#requireSqlIdentifier} 校验，请求参数只能提供资源码。
 * 登记项不合法（重复资源码、表名列名形态不对）时直接让启动失败，不留到运行期才在取号处报错。</p>
 *
 * <p>用 {@link ObjectProvider} 而不是 {@code List} 注入：干净的新项目一个业务模块都没有，
 * 零个候选时 {@code List} 注入会让启动直接失败。</p>
 */
@Component
public class CodeRuleRegistry {

    private final Map<String, CodeRuleSpec> specs;

    public CodeRuleRegistry(ObjectProvider<CodeRuleContributor> contributors) {
        Map<String, CodeRuleSpec> collected = new LinkedHashMap<>();
        contributors.forEach(contributor -> contributor.specs().forEach(spec -> {
            validate(spec);
            CodeRuleSpec previous = collected.putIfAbsent(spec.resourceCode(), spec);
            if (previous != null) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "编号资源码重复登记:" + spec.resourceCode());
            }
        }));
        this.specs = Collections.unmodifiableMap(collected);
    }

    /** 全部已登记资源，按登记顺序（跨模块时即各模块入口的登记顺序）。 */
    public Map<String, CodeRuleSpec> all() {
        return specs;
    }

    /** 按资源码取登记项，未登记即拒绝——不返回 null，免得调用方拿着空规格去拼 SQL。 */
    public CodeRuleSpec require(String resourceCode) {
        CodeRuleSpec spec = specs.get(resourceCode);
        if (spec == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "未登记的编码资源:" + resourceCode);
        }
        return spec;
    }

    private void validate(CodeRuleSpec spec) {
        CodePattern.requireSqlIdentifier(spec.resourceCode(), "编码资源码");
        CodePattern.requireSqlIdentifier(spec.tableName(), "编号所在表名");
        CodePattern.requireSqlIdentifier(spec.columnName(), "编号所在列名");
        CodePattern.requirePrefix(spec.prefix());
        CodePattern.requireSeparator(spec.separator());
        CodePattern.requireDatePattern(spec.datePattern());
        CodePattern.requireSerialLength(spec.serialLength());
        if (spec.label() == null || spec.label().isBlank()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                "编码资源缺少中文展示名:" + spec.resourceCode());
        }
    }
}
