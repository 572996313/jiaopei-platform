package com.cornerstone.core.common.model;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页排序白名单应用器的行为守护。
 */
class PageSortSupportTest {

    private static final Map<String, SFunction<UserAccountEntity, ?>> SORTABLE = Map.of(
        "username", UserAccountEntity::getUsername,
        "displayName", UserAccountEntity::getDisplayName,
        "lastLoginAt", UserAccountEntity::getLastLoginAt);

    @BeforeAll
    static void initLambdaCache() {
        // 断言里要把 lambda 解析成列名，必须先注册 TableInfo（生产由 mapper 扫描完成）
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccountEntity.class);
    }

    private final LambdaQueryWrapper<UserAccountEntity> wrapper = new LambdaQueryWrapper<>();

    private PageQuery query(String sortField, String sortOrder) {
        PageQuery query = new PageQuery();
        query.setSortField(sortField);
        query.setSortOrder(sortOrder);
        return query;
    }

    private String orderBySql() {
        return wrapper.getExpression().getOrderBy().stream()
            .map(segment -> segment.getSqlSegment())
            .collect(Collectors.joining(","));
    }

    @Test
    void 未传排序字段时不追加排序() {
        assertFalse(PageSortSupport.apply(wrapper, query(null, "desc"), SORTABLE, UserAccountEntity::getId));
        assertFalse(PageSortSupport.apply(wrapper, query("  ", null), SORTABLE, UserAccountEntity::getId));

        assertTrue(wrapper.getExpression().getOrderBy().isEmpty());
    }

    @Test
    void 排序列后必须再压一个稳定列() {
        assertTrue(PageSortSupport.apply(wrapper, query("username", "asc"), SORTABLE, UserAccountEntity::getId));

        String sql = orderBySql();
        assertTrue(sql.contains("username"), sql);
        assertTrue(sql.contains("id"), sql);
    }

    @Test
    void 方向缺省为升序() {
        assertTrue(PageSortSupport.apply(wrapper, query("username", null), SORTABLE, UserAccountEntity::getId));

        assertTrue(orderBySql().contains("ASC"), orderBySql());
    }

    @Test
    void 降序大小写不敏感() {
        assertTrue(PageSortSupport.apply(wrapper, query("lastLoginAt", "DESC"), SORTABLE, UserAccountEntity::getId));

        assertTrue(orderBySql().contains("DESC"), orderBySql());
    }

    @Test
    void 白名单外字段报错并列出可选值() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> PageSortSupport.apply(wrapper, query("passwordHash", "asc"), SORTABLE, UserAccountEntity::getId));

        assertEquals("不支持的排序字段：passwordHash，可选值：displayName、lastLoginAt、username",
            exception.getMessage());
        assertTrue(wrapper.getExpression().getOrderBy().isEmpty());
    }

    @Test
    void 非法排序方向报错() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> PageSortSupport.apply(wrapper, query("username", "ascending"), SORTABLE, UserAccountEntity::getId));

        assertEquals("排序方向只支持 asc 或 desc，当前值：ascending", exception.getMessage());
    }
}
