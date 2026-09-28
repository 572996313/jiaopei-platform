package com.cornerstone.core.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.code.CodePattern;
import com.cornerstone.core.system.code.CodeRuleRegistry;
import com.cornerstone.core.system.code.CodeRuleSpec;
import com.cornerstone.core.system.domain.entity.CodeRuleEntity;
import com.cornerstone.core.system.dto.CodeRuleUpdateRequest;
import com.cornerstone.core.system.dto.CodeRuleView;
import com.cornerstone.core.system.mapper.CodeRuleMapper;
import com.cornerstone.core.system.mapper.CodeSequenceMapper;
import com.cornerstone.core.system.service.CodeRuleService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 编号规则与取号引擎。
 *
 * <p>取号只做三件事：按当前日期段拼出号段前缀 → 找该段内库里最大的同号 → 尾巴加一并补零。
 * 不建序列表、不加行锁（一期口径：允许断号，现场并发低），所以"点了生成但没保存"会留下一个空号。</p>
 *
 * <p>"该段最大同号"与"候选号是否被占"两条查询刻意走原生 SQL：MyBatis-Plus 的逻辑删除会给它们
 * 自动加上 {@code deleted = 0}，而软删墓碑照样占着业务表的编码唯一键（那批唯一键不含 deleted，
 * 见 V72 的收口形态）。查号时把墓碑算进去，才不会生成一个"看着能用、存进去必撞键"的号。</p>
 */
@Service
public class CodeRuleServiceImpl implements CodeRuleService {

    /** 让号次数上限：候选号被占就再 +1 试一次。绝大多数一次就过，撞满说明号段真被填满了。 */
    private static final int MAX_COLLISION_ATTEMPTS = 20;

    private final CodeRuleRegistry registry;
    private final CodeRuleMapper codeRuleMapper;
    private final CodeSequenceMapper sequenceMapper;

    public CodeRuleServiceImpl(CodeRuleRegistry registry,
                               CodeRuleMapper codeRuleMapper,
                               CodeSequenceMapper sequenceMapper) {
        this.registry = registry;
        this.codeRuleMapper = codeRuleMapper;
        this.sequenceMapper = sequenceMapper;
    }

    @Override
    public List<CodeRuleView> listRules() {
        Map<String, CodeRuleEntity> rows = codeRuleMapper
            .selectList(new LambdaQueryWrapper<CodeRuleEntity>())
            .stream()
            .collect(Collectors.toMap(CodeRuleEntity::getResourceCode, Function.identity(), (first, second) -> first));
        List<CodeRuleView> views = new ArrayList<>();
        for (CodeRuleSpec spec : registry.all().values()) {
            views.add(toView(spec, rows.get(spec.resourceCode())));
        }
        return views;
    }

    @Override
    @Transactional
    public CodeRuleView updateRule(String resourceCode, CodeRuleUpdateRequest request) {
        CodeRuleSpec spec = registry.require(resourceCode);
        CodePattern.requirePrefix(request.prefix());
        CodePattern.requireSeparator(nullToEmpty(request.separator()));
        CodePattern.requireDatePattern(nullToEmpty(request.datePattern()));
        CodePattern.requireSerialLength(request.serialLength());

        CodeRuleEntity existing = findRuleRow(resourceCode);
        if (existing == null) {
            CodeRuleEntity created = new CodeRuleEntity();
            created.setResourceCode(resourceCode);
            created.setDeleted(Boolean.FALSE);
            fill(created, request);
            try {
                codeRuleMapper.insert(created);
            } catch (DuplicateKeyException exception) {
                // 同一资源被并发建过两次:唯一键已经挡住了,改成更新那一条(不吞异常,该改还得改)
                CodeRuleEntity raced = findRuleRow(resourceCode);
                if (raced == null) {
                    throw exception;
                }
                fill(raced, request);
                codeRuleMapper.updateById(raced);
            }
        } else {
            fill(existing, request);
            codeRuleMapper.updateById(existing);
        }
        return toView(spec, findRuleRow(resourceCode));
    }

    @Override
    public String nextCode(String resourceCode) {
        CodeRuleSpec spec = registry.require(resourceCode);
        EffectiveFormat format = effectiveFormat(spec, findRuleRow(resourceCode));
        if (!format.enabled()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, spec.label() + "未启用自动编号,请手工填写");
        }
        return buildNextCode(spec, format);
    }

    @Override
    public String resolveForCreate(String resourceCode, String submittedCode) {
        CodeRuleSpec spec = registry.require(resourceCode);
        if (StringUtils.hasText(submittedCode)) {
            return submittedCode.trim();
        }
        EffectiveFormat format = effectiveFormat(spec, findRuleRow(resourceCode));
        if (!format.enabled()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, spec.label() + "不能为空");
        }
        return buildNextCode(spec, format);
    }

    @Override
    public List<String> listEnabledResourceCodes() {
        // 只报"登记过且已启用"的：规则行能留着但资源可能已不再登记（模块没装配、资源被改名），
        // 那种情况下按钮亮着、点下去必报"未登记的编码资源"，比不显示更糟。
        return codeRuleMapper
            .selectList(new LambdaQueryWrapper<CodeRuleEntity>()
                .eq(CodeRuleEntity::getEnabled, Boolean.TRUE))
            .stream()
            .map(CodeRuleEntity::getResourceCode)
            .filter(registry.all()::containsKey)
            .sorted()
            .collect(Collectors.toList());
    }

    @Override
    public String autoCodeIfEnabled(String resourceCode) {
        CodeRuleSpec spec = registry.require(resourceCode);
        EffectiveFormat format = effectiveFormat(spec, findRuleRow(resourceCode));
        return format.enabled() ? buildNextCode(spec, format) : null;
    }

    /** 拼号段前缀 → 取该段最大同号 → 续排，候选号被占（含墓碑）就继续 +1。 */
    private String buildNextCode(CodeRuleSpec spec, EffectiveFormat format) {
        String segment = CodePattern.segment(format.prefix(), format.separator(),
            format.datePattern(), LocalDate.now());
        String lastCode = sequenceMapper.selectLastCodeBySegment(
            spec.tableName(), spec.columnName(), CodePattern.likePrefix(segment));
        long sequence = CodePattern.sequenceAfter(lastCode) + 1;
        for (int attempt = 0; attempt < MAX_COLLISION_ATTEMPTS; attempt++) {
            String candidate = CodePattern.format(segment, sequence, format.serialLength());
            if (sequenceMapper.selectCodeOccupied(spec.tableName(), spec.columnName(), candidate) == null) {
                return candidate;
            }
            sequence++;
        }
        throw new BusinessException(ResultCode.INTERNAL_ERROR,
            spec.label() + "当前号段已无可用编号,请加大序号位数或改日期段粒度");
    }

    /**
     * 组装一行视图：格式取"生效值"（有规则行整份按行、没行才用代码登记的默认），
     * 默认值一并给出，供维护页表单首次预填。未启用的资源不给预览号。
     */
    private CodeRuleView toView(CodeRuleSpec spec, CodeRuleEntity row) {
        EffectiveFormat format = effectiveFormat(spec, row);
        return new CodeRuleView(
            row == null ? null : row.getId(),
            spec.resourceCode(),
            spec.label(),
            row != null,
            format.enabled(),
            format.prefix(),
            format.separator(),
            format.datePattern(),
            format.serialLength(),
            spec.prefix(),
            spec.separator(),
            spec.datePattern(),
            spec.serialLength(),
            format.enabled() ? buildNextCode(spec, format) : null,
            row == null ? null : row.getRemark());
    }

    private void fill(CodeRuleEntity entity, CodeRuleUpdateRequest request) {
        entity.setEnabled(request.enabled());
        entity.setPrefix(request.prefix());
        entity.setSeparatorChar(nullToEmpty(request.separator()));
        entity.setDatePattern(nullToEmpty(request.datePattern()));
        entity.setSerialLength(request.serialLength());
        entity.setRemark(request.remark());
    }

    /**
     * 生效格式：有规则行就整份按行里的值走，没有行才用代码登记的默认值。
     *
     * <p>不给"某个字段留空即回落默认"留余地——分隔符与日期段的空串本身有语义
     * （各段直接相连 / 不带日期段即全局累加），兼任哨兵就分不清了，见 V108 头部口径 1。</p>
     */
    private EffectiveFormat effectiveFormat(CodeRuleSpec spec, CodeRuleEntity row) {
        if (row == null) {
            return new EffectiveFormat(false, spec.prefix(), spec.separator(),
                spec.datePattern(), spec.serialLength());
        }
        return new EffectiveFormat(Boolean.TRUE.equals(row.getEnabled()), row.getPrefix(),
            nullToEmpty(row.getSeparatorChar()), nullToEmpty(row.getDatePattern()),
            row.getSerialLength() == null ? spec.serialLength() : row.getSerialLength());
    }

    private CodeRuleEntity findRuleRow(String resourceCode) {
        return codeRuleMapper.selectOne(new LambdaQueryWrapper<CodeRuleEntity>()
            .eq(CodeRuleEntity::getResourceCode, resourceCode));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 一份生效格式的快照,免得取号链路上反复在"行值 or 默认值"之间判断。 */
    private record EffectiveFormat(boolean enabled, String prefix, String separator,
                                   String datePattern, int serialLength) {
    }
}
