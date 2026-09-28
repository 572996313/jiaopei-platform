package com.cornerstone.core.system;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.cornerstone.core.system.service.impl.CodeRuleServiceImpl;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 取号引擎的行为守护（不连库）。
 *
 * <p>钉住四件容易被改坏的事：没启用就一个号都不给（并且报的是中文展示名）；号是从"当天号段里
 * 库里最大的同号"续出来的，包含软删墓碑；候选号被占就继续加一而不是报错；操作员自己填了号就
 * 一律用他填的。</p>
 */
class CodeRuleServiceImplTest {

    /** 与 module-education 将来登记的学员资源同形态，测试里自己造，避免依赖业务模块。 */
    private static final CodeRuleSpec STUDENT =
        new CodeRuleSpec("student", "学员编号", "jp_student", "student_no", "STU", "-", "yyyyMMdd", 3);

    /** 当天号段前缀，如 STU-20260928-。 */
    private static final String SEGMENT = CodePattern.segment(
        "STU", "-", "yyyyMMdd", LocalDate.now());

    private final CodeRuleRegistry registry = mock(CodeRuleRegistry.class);
    private final CodeRuleMapper codeRuleMapper = mock(CodeRuleMapper.class);
    private final CodeSequenceMapper sequenceMapper = mock(CodeSequenceMapper.class);
    private final CodeRuleServiceImpl service =
        new CodeRuleServiceImpl(registry, codeRuleMapper, sequenceMapper);

    @BeforeAll
    static void initLambdaCache() {
        // findRuleRow 用 lambda 取列名，生产由 mapper 扫描注册 TableInfo，单测要自己补一次
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), CodeRuleEntity.class);
    }

    /** 规则行：启用、按日重排、3 位序号。 */
    private static CodeRuleEntity enabledRow() {
        CodeRuleEntity row = new CodeRuleEntity();
        row.setId(9L);
        row.setResourceCode("student");
        row.setEnabled(Boolean.TRUE);
        row.setPrefix("STU");
        row.setSeparatorChar("-");
        row.setDatePattern("yyyyMMdd");
        row.setSerialLength(3);
        row.setRemark("按日重排");
        return row;
    }

    private void givenRegistryKnowsStudent() {
        when(registry.require("student")).thenReturn(STUDENT);
    }

    private void givenRuleRow(CodeRuleEntity row) {
        when(codeRuleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);
    }

    /**
     * 号段内没有任何已占用的号，最大同号就是传入的那个（可为 null）。
     *
     * <p>occupied 必须显式 stub 成 null：不 stub 时 Mockito 对 {@code Integer} 返回 0，取号侧会把 0
     * 读成"这个号已被占"，于是一直让号到上限、报"无可用编号"。库里这条探针只返回 1 或无行，
     * 所以生产不存在这个歧义，但它让用例必须写全桩。</p>
     */
    private void givenNoCodeTaken(String lastCode) {
        when(sequenceMapper.selectLastCodeBySegment(anyString(), anyString(), anyString())).thenReturn(lastCode);
        when(sequenceMapper.selectCodeOccupied(anyString(), anyString(), anyString())).thenReturn(null);
    }

    @Test
    void 还没配置过规则时不能取号并且不查库() {
        givenRegistryKnowsStudent();
        givenRuleRow(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.nextCode("student"));

        assertTrue(exception.getMessage().contains("学员编号"), exception.getMessage());
        assertTrue(exception.getMessage().contains("未启用自动编号"), exception.getMessage());
        // 未启用就不该白扫一次业务表
        verifyNoInteractions(sequenceMapper);
    }

    @Test
    void 已配置但未启用时同样拒绝取号() {
        givenRegistryKnowsStudent();
        CodeRuleEntity row = enabledRow();
        row.setEnabled(Boolean.FALSE);
        givenRuleRow(row);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.nextCode("student"));

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        verifyNoInteractions(sequenceMapper);
    }

    @Test
    void 取号是在当天号段内续排并且按登记到的表列查询() {
        givenRegistryKnowsStudent();
        givenRuleRow(enabledRow());
        givenNoCodeTaken(SEGMENT + "007");

        assertEquals(SEGMENT + "008", service.nextCode("student"));

        ArgumentCaptor<String> table = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> column = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> like = ArgumentCaptor.forClass(String.class);
        verify(sequenceMapper).selectLastCodeBySegment(table.capture(), column.capture(), like.capture());
        // 拼进 SQL 的名字只能来自代码登记的清单，不是请求参数
        assertEquals("jp_student", table.getValue());
        assertEquals("student_no", column.getValue());
        assertEquals(CodePattern.likePrefix(SEGMENT), like.getValue());
    }

    @Test
    void 跨天后同号段查不到就回到001() {
        givenRegistryKnowsStudent();
        givenRuleRow(enabledRow());
        // 库里只有昨天的号，当天的 LIKE 前缀扫不到
        givenNoCodeTaken(null);

        assertEquals(SEGMENT + "001", service.nextCode("student"));
    }

    @Test
    void 异形编号解析不出序号时从001起让号() {
        givenRegistryKnowsStudent();
        givenRuleRow(enabledRow());
        // 手工填的尾巴不是数字，解析成 0；001 已被占用则继续加一
        when(sequenceMapper.selectLastCodeBySegment(anyString(), anyString(), anyString()))
            .thenReturn(SEGMENT + "A");
        when(sequenceMapper.selectCodeOccupied(anyString(), anyString(), anyString()))
            .thenReturn(1)
            .thenReturn(null);

        assertEquals(SEGMENT + "002", service.nextCode("student"));
    }

    @Test
    void 序号超出位数时自动加宽而不是截断() {
        givenRegistryKnowsStudent();
        givenRuleRow(enabledRow());
        givenNoCodeTaken(SEGMENT + "999");

        assertEquals(SEGMENT + "1000", service.nextCode("student"));
    }

    @Test
    void 号段被占满时报中文错并且只让号二十次() {
        givenRegistryKnowsStudent();
        givenRuleRow(enabledRow());
        when(sequenceMapper.selectLastCodeBySegment(anyString(), anyString(), anyString()))
            .thenReturn(SEGMENT + "001");
        when(sequenceMapper.selectCodeOccupied(anyString(), anyString(), anyString())).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.nextCode("student"));

        assertEquals(ResultCode.INTERNAL_ERROR, exception.getResultCode());
        assertTrue(exception.getMessage().contains("无可用编号"), exception.getMessage());
        verify(sequenceMapper, times(20)).selectCodeOccupied(anyString(), anyString(), anyString());
    }

    @Test
    void 操作员填了编号就用人家的且不碰生成逻辑() {
        givenRegistryKnowsStudent();

        assertEquals("SELF-001", service.resolveForCreate("student", "  SELF-001  "));

        verifyNoInteractions(codeRuleMapper);
        verifyNoInteractions(sequenceMapper);
    }

    @Test
    void 没启用规则时留空仍报不能为空() {
        givenRegistryKnowsStudent();
        givenRuleRow(null);

        BusinessException exception =
            assertThrows(BusinessException.class, () -> service.resolveForCreate("student", "   "));

        assertEquals("学员编号不能为空", exception.getMessage());
        verifyNoInteractions(sequenceMapper);
    }

    @Test
    void 启用规则时留空由服务端补号() {
        givenRegistryKnowsStudent();
        givenRuleRow(enabledRow());
        givenNoCodeTaken(SEGMENT + "012");

        assertEquals(SEGMENT + "013", service.resolveForCreate("student", null));
    }

    @Test
    void 未登记的资源码一律拒绝() {
        when(registry.require("unknown")).thenThrow(
            new BusinessException(ResultCode.BAD_REQUEST, "未登记的编码资源:unknown"));

        BusinessException exception =
            assertThrows(BusinessException.class, () -> service.nextCode("unknown"));

        assertEquals("未登记的编码资源:unknown", exception.getMessage());
        verifyNoInteractions(sequenceMapper);
    }

    @Test
    void 列表按登记清单出行没配置的资源也在() {
        CodeRuleSpec classroom =
            new CodeRuleSpec("classroom", "教室编号", "jp_classroom", "room_no", "ROM", "-", "", 2);
        Map<String, CodeRuleSpec> specs = new LinkedHashMap<>();
        specs.put("student", STUDENT);
        specs.put("classroom", classroom);
        when(registry.all()).thenReturn(specs);
        // 只有学员配过规则，且没启用
        CodeRuleEntity row = enabledRow();
        row.setEnabled(Boolean.FALSE);
        when(codeRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(row));

        List<CodeRuleView> views = service.listRules();

        assertEquals(2, views.size());
        CodeRuleView student = views.get(0);
        assertEquals("student", student.resourceCode());
        assertTrue(student.configured());
        assertFalse(student.enabled());
        assertNull(student.previewCode());
        assertEquals("按日重排", student.remark());

        CodeRuleView classroomView = views.get(1);
        assertEquals("classroom", classroomView.resourceCode());
        assertFalse(classroomView.configured());
        assertNull(classroomView.id());
        assertFalse(classroomView.enabled());
        // 没规则行时展示代码登记的默认格式，供表单首次预填
        assertEquals("ROM", classroomView.prefix());
        assertEquals("", classroomView.datePattern());
        assertEquals(2, classroomView.defaultSerialLength());
        assertNull(classroomView.previewCode());
        verifyNoInteractions(sequenceMapper);
    }

    @Test
    void 启用的资源在列表里给出下一个号预览() {
        when(registry.all()).thenReturn(Map.of("student", STUDENT));
        when(codeRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(enabledRow()));
        givenNoCodeTaken(SEGMENT + "003");

        List<CodeRuleView> views = service.listRules();

        assertEquals(SEGMENT + "004", views.get(0).previewCode());
    }

    @Test
    void 首次保存插入规则行并归一空字段() {
        givenRegistryKnowsStudent();
        CodeRuleEntity inserted = enabledRow();
        inserted.setId(11L);
        when(codeRuleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, inserted);
        givenNoCodeTaken(null);

        CodeRuleView view = service.updateRule("student",
            new CodeRuleUpdateRequest(true, "STU", null, null, 3, "备注"));

        ArgumentCaptor<CodeRuleEntity> captor = ArgumentCaptor.forClass(CodeRuleEntity.class);
        verify(codeRuleMapper).insert(captor.capture());
        CodeRuleEntity saved = captor.getValue();
        assertEquals("student", saved.getResourceCode());
        assertEquals(Boolean.FALSE, saved.getDeleted());
        // null 与空串存成同一个语义：不留 NULL，读取侧就不必两种都判
        assertEquals("", saved.getSeparatorChar());
        assertEquals("", saved.getDatePattern());
        assertEquals("备注", saved.getRemark());
        assertTrue(view.configured());
        assertTrue(view.enabled());
    }

    @Test
    void 已有规则行时按资源码更新() {
        givenRegistryKnowsStudent();
        CodeRuleEntity existing = enabledRow();
        when(codeRuleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing, existing);

        service.updateRule("student", new CodeRuleUpdateRequest(false, "S", "", "yyyyMM", 4, null));

        verify(codeRuleMapper, never()).insert(any(CodeRuleEntity.class));
        assertEquals("S", existing.getPrefix());
        assertEquals(Integer.valueOf(4), existing.getSerialLength());
        assertEquals(Boolean.FALSE, existing.getEnabled());
        ArgumentCaptor<CodeRuleEntity> captor = ArgumentCaptor.forClass(CodeRuleEntity.class);
        verify(codeRuleMapper).updateById(captor.capture());
        assertEquals(Long.valueOf(9L), captor.getValue().getId());
    }

    @Test
    void 并发建行撞唯一键时改成更新那一条() {
        givenRegistryKnowsStudent();
        CodeRuleEntity raced = enabledRow();
        when(codeRuleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, raced, raced);
        givenNoCodeTaken(null);
        when(codeRuleMapper.insert(any(CodeRuleEntity.class)))
            .thenThrow(new DuplicateKeyException("dup"));

        CodeRuleView view = service.updateRule("student",
            new CodeRuleUpdateRequest(true, "STU", "-", "yyyyMMdd", 3, null));

        verify(codeRuleMapper).updateById(any(CodeRuleEntity.class));
        assertTrue(view.enabled());
    }

    @Test
    void 非法格式在落库前就拒绝() {
        givenRegistryKnowsStudent();

        assertThrows(BusinessException.class, () -> service.updateRule("student",
            new CodeRuleUpdateRequest(true, "STU%", "-", "yyyyMMdd", 3, null)));
        assertThrows(BusinessException.class, () -> service.updateRule("student",
            new CodeRuleUpdateRequest(true, "STU", "/", "yyyyMMdd", 3, null)));
        assertThrows(BusinessException.class, () -> service.updateRule("student",
            new CodeRuleUpdateRequest(true, "STU", "-", "yyyyMMddHHmmss", 3, null)));
        assertThrows(BusinessException.class, () -> service.updateRule("student",
            new CodeRuleUpdateRequest(true, "STU", "-", "yyyyMMdd", 0, null)));

        verify(codeRuleMapper, never()).insert(any(CodeRuleEntity.class));
        verify(codeRuleMapper, never()).updateById(any(CodeRuleEntity.class));
    }

    @Test
    void 保存未登记的资源码时不动库() {
        when(registry.require("parent")).thenThrow(
            new BusinessException(ResultCode.BAD_REQUEST, "未登记的编码资源:parent"));

        assertThrows(BusinessException.class, () -> service.updateRule("parent",
            new CodeRuleUpdateRequest(true, "PAR", "-", "", 3, null)));

        verifyNoInteractions(codeRuleMapper);
    }

    /**
     * 前端「生成」按钮的显隐依据：只报"已启用且仍然登记着"的资源。
     *
     * <p>规则行不会被模块卸载删掉，所以"启用中的行"多于"能取号的资源"是常态（关了 organization、
     * 或资源改了码）。把那种资源报出去，按钮会亮着、点下去必报"未登记的编码资源"。</p>
     */
    @Test
    void 启用的资源码只报仍然登记着的() {
        Map<String, CodeRuleSpec> specs = new LinkedHashMap<>();
        specs.put("student", STUDENT);
        specs.put("class", new CodeRuleSpec("class", "班级编码", "jp_class", "class_code",
            "CLS", "-", "yyyyMM", 3));
        when(registry.all()).thenReturn(specs);
        // selectList 的 enabled=1 条件在 SQL 里，桩给的就是库中"启用中的行"；campus 那条已不再登记
        CodeRuleEntity studentEnabled = enabledRow();
        CodeRuleEntity orphanEnabled = enabledRow();
        orphanEnabled.setResourceCode("campus");
        when(codeRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(orphanEnabled, studentEnabled));

        assertEquals(List.of("student"), service.listEnabledResourceCodes());
    }

    @Test
    void 日期段按登记的粒度参与拼号() {
        CodeRuleSpec byYear =
            new CodeRuleSpec("student", "学员编号", "jp_student", "student_no", "STU", "-", "yyyy", 3);
        when(registry.require("student")).thenReturn(byYear);
        CodeRuleEntity row = enabledRow();
        row.setDatePattern("yyyy");
        givenRuleRow(row);
        String yearly = CodePattern.segment("STU", "-", "yyyy", LocalDate.now());
        when(sequenceMapper.selectLastCodeBySegment(eq("jp_student"), eq("student_no"), anyString()))
            .thenReturn(yearly + "021");
        when(sequenceMapper.selectCodeOccupied(anyString(), anyString(), anyString())).thenReturn(null);

        assertEquals(yearly + "022", service.nextCode("student"));
    }
}
