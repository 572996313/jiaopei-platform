package com.cornerstone.core.system;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.code.CodeRuleContributor;
import com.cornerstone.core.system.code.CodeRuleRegistry;
import com.cornerstone.core.system.code.CodeRuleSpec;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编号资源注册表的守护。
 *
 * <p>这张表是取号 SQL 里表名/列名的唯一来源，所以它的入口必须比业务代码更严：登记项形态不对、
 * 资源码重复、缺中文展示名，都要在启动期就把容器炸掉，而不是留到运行期在拼 SQL 的地方报错。</p>
 */
class CodeRuleRegistryTest {

    private static final CodeRuleSpec STUDENT =
        new CodeRuleSpec("student", "学员编号", "jp_student", "student_no", "STU", "-", "yyyyMMdd", 3);

    /**
     * 用一个真实的 ObjectProvider 而不是 mock：注册表走的是 Iterable 的默认方法 forEach，
     * mock 上这个方法会被打桩成空实现，测试会"通过"但其实一条都没登记。
     */
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
                return items.size() == 1 ? getObject() : null;
            }

            @Override
            public Iterator<CodeRuleContributor> iterator() {
                return items.iterator();
            }
        };
    }

    private static CodeRuleContributor contributor(CodeRuleSpec... specs) {
        return () -> List.of(specs);
    }

    @Test
    void 一个登记方都没有时也能装配() {
        // 干净的新项目没有任何业务模块，此时必须起得来，只是无号可取
        CodeRuleRegistry registry = new CodeRuleRegistry(providersOf());

        assertTrue(registry.all().isEmpty());
        BusinessException exception =
            assertThrows(BusinessException.class, () -> registry.require("student"));
        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        assertEquals("未登记的编码资源:student", exception.getMessage());
    }

    @Test
    void 多模块登记按登记顺序收成一张表() {
        CodeRuleSpec classroom =
            new CodeRuleSpec("classroom", "教室编号", "jp_classroom", "room_no", "ROM", "-", "", 2);

        CodeRuleRegistry registry = new CodeRuleRegistry(
            providersOf(contributor(STUDENT), contributor(classroom)));

        assertEquals(List.of("student", "classroom"), List.copyOf(registry.all().keySet()));
        assertEquals("jp_student", registry.require("student").tableName());
        assertEquals("jp_classroom", registry.require("classroom").tableName());
    }

    @Test
    void 登记出来的清单不许被改() {
        CodeRuleRegistry registry = new CodeRuleRegistry(providersOf(contributor(STUDENT)));

        assertThrows(UnsupportedOperationException.class,
            () -> registry.all().put("other", STUDENT));
    }

    @Test
    void 资源码重复登记时启动即失败() {
        BusinessException exception = assertThrows(BusinessException.class, () -> new CodeRuleRegistry(
            providersOf(contributor(STUDENT), contributor(STUDENT))));

        assertEquals(ResultCode.INTERNAL_ERROR, exception.getResultCode());
        assertTrue(exception.getMessage().contains("编号资源码重复登记:student"), exception.getMessage());
    }

    @Test
    void 表名列名形态不对一律拒绝() {
        // 这两条名字要拼进 SQL，白名单不收任何东西
        assertThrows(BusinessException.class, () -> new CodeRuleRegistry(
            providersOf(contributor(new CodeRuleSpec("student", "学员编号",
                "jp_student; DROP TABLE jp_student", "student_no", "STU", "-", "", 3)))));
        assertThrows(BusinessException.class, () -> new CodeRuleRegistry(
            providersOf(contributor(new CodeRuleSpec("student", "学员编号",
                "jp_student", "StudentNo", "STU", "-", "", 3)))));
        assertThrows(BusinessException.class, () -> new CodeRuleRegistry(
            providersOf(contributor(new CodeRuleSpec("student", "学员编号",
                "1_student", "student_no", "STU", "-", "", 3)))));
        assertThrows(BusinessException.class, () -> new CodeRuleRegistry(
            providersOf(contributor(new CodeRuleSpec("Student", "学员编号",
                "jp_student", "student_no", "STU", "-", "", 3)))));
    }

    @Test
    void 默认格式不合法也拦在登记期() {
        assertThrows(BusinessException.class, () -> new CodeRuleRegistry(
            providersOf(contributor(new CodeRuleSpec("student", "学员编号",
                "jp_student", "student_no", "STU", "--", "", 3)))));
        assertThrows(BusinessException.class, () -> new CodeRuleRegistry(
            providersOf(contributor(new CodeRuleSpec("student", "学员编号",
                "jp_student", "student_no", "STU", "-", "yyyyMMddHHmmss", 3)))));
        assertThrows(BusinessException.class, () -> new CodeRuleRegistry(
            providersOf(contributor(new CodeRuleSpec("student", "学员编号",
                "jp_student", "student_no", "STU", "-", "", 11)))));
    }

    @Test
    void 缺少中文展示名时拒绝() {
        // 展示名进了报错文案，空着会让操作员看到一句没有主语的提示
        BusinessException exception = assertThrows(BusinessException.class, () -> new CodeRuleRegistry(
            providersOf(contributor(new CodeRuleSpec("student", "  ",
                "jp_student", "student_no", "STU", "-", "", 3)))));

        assertTrue(exception.getMessage().contains("缺少中文展示名"), exception.getMessage());
    }
}
