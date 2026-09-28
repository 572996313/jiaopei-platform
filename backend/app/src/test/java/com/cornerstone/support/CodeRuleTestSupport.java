package com.cornerstone.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.system.code.CodeRuleContributor;
import com.cornerstone.core.system.code.CodeRuleRegistry;
import com.cornerstone.core.system.code.CodeRuleSpec;
import com.cornerstone.core.system.code.InstitutionCodeRuleContributor;
import com.cornerstone.core.system.domain.entity.CodeRuleEntity;
import com.cornerstone.core.system.mapper.CodeRuleMapper;
import com.cornerstone.core.system.mapper.CodeSequenceMapper;
import com.cornerstone.core.system.service.CodeRuleService;
import com.cornerstone.core.system.service.impl.CodeRuleServiceImpl;
import com.cornerstone.module.education.config.EducationCodeRuleContributor;
import com.cornerstone.module.organization.config.OrganizationCodeRuleContributor;
import java.util.Iterator;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 单测用的编号服务替身：一份**真实实现 + 零规则行**，等价于"12 个资源都还没启用自动编号"。
 *
 * <p>为什么不用 {@code mock(CodeRuleService.class)}：业务 Service 现在都要调
 * {@code resolveForCreate}，mock 默认回 null 会让一堆与编号无关的老用例凭空 NPE，
 * 或者逼着每个用例补一行 {@code given(...willReturn(提交的值))}。这里直接用真实现，
 * 行为与线上一致：操作员填了就用人家的（trim 后原样返回），留空就报"学号不能为空"。</p>
 *
 * <p>要测"启用规则后自动补号"的用例用 {@link #rulesEnabledFor}。</p>
 */
public final class CodeRuleTestSupport {

    private CodeRuleTestSupport() {
    }

    static {
        // findRuleRow 用 lambda 取列名，生产由 mapper 扫描注册 TableInfo，单测只在这里补一次
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), CodeRuleEntity.class);
    }

    /** 未配置任何规则的编号服务（mapper 不桩：selectOne 回 null 即"该资源没有规则行"）。 */
    public static CodeRuleService unconfiguredRules() {
        return new CodeRuleServiceImpl(fullRegistry(), mock(CodeRuleMapper.class), mock(CodeSequenceMapper.class));
    }

    /**
     * 让某一个资源"已启用自动编号"的编号服务，格式取代码登记的默认值。
     *
     * <p>规则行与号段都桩在 mock mapper 上，所以一个实例只喂给这一个资源的用例；
     * {@code lastCode} 是号段里库里最大的同号（传 null 表示这段还没号，从 001 起），
     * 于是补出来的号就是它 +1，用例可以直接断言。</p>
     */
    public static CodeRuleService rulesEnabledFor(String resourceCode, String lastCode) {
        CodeRuleRegistry codeRuleRegistry = fullRegistry();
        CodeRuleSpec spec = codeRuleRegistry.require(resourceCode);
        CodeRuleEntity row = new CodeRuleEntity();
        row.setResourceCode(resourceCode);
        row.setEnabled(Boolean.TRUE);
        row.setPrefix(spec.prefix());
        row.setSeparatorChar(spec.separator());
        row.setDatePattern(spec.datePattern());
        row.setSerialLength(spec.serialLength());

        CodeRuleMapper codeRuleMapper = mock(CodeRuleMapper.class);
        when(codeRuleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);
        CodeSequenceMapper sequenceMapper = mock(CodeSequenceMapper.class);
        when(sequenceMapper.selectLastCodeBySegment(anyString(), anyString(), anyString())).thenReturn(lastCode);
        // 探针桩成"没占用"：不桩时 Mockito 对 Integer 回 0，取号侧会读成已占用而一直让号
        when(sequenceMapper.selectCodeOccupied(anyString(), anyString(), anyString())).thenReturn(null);
        return new CodeRuleServiceImpl(codeRuleRegistry, codeRuleMapper, sequenceMapper);
    }

    /** 三个模块的登记表原样拼出来：用于按资源码找它的表列与展示名。 */
    public static CodeRuleRegistry fullRegistry() {
        return new CodeRuleRegistry(providersOf(
            new InstitutionCodeRuleContributor(),
            new OrganizationCodeRuleContributor(),
            new EducationCodeRuleContributor()));
    }

    /** 必须给真实的 ObjectProvider：注册表走 Iterable 的默认方法 forEach，mock 会一条都收不到。 */
    private static ObjectProvider<CodeRuleContributor> providersOf(CodeRuleContributor... contributors) {
        List<CodeRuleContributor> items = List.of(contributors);
        return new ObjectProvider<>() {
            @Override
            public CodeRuleContributor getObject() {
                return items.isEmpty() ? null : items.get(0);
            }

            @Override
            public CodeRuleContributor getObject(Object... args) {
                return getObject();
            }

            @Override
            public CodeRuleContributor getIfAvailable() {
                return getObject();
            }

            @Override
            public CodeRuleContributor getIfUnique() {
                return getObject();
            }

            @Override
            public Iterator<CodeRuleContributor> iterator() {
                return items.iterator();
            }
        };
    }
}
