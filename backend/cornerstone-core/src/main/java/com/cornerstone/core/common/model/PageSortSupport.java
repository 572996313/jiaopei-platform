package com.cornerstone.core.common.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;

import java.util.Map;
import java.util.TreeSet;

/**
 * 分页列表的服务端单列排序支持。
 *
 * <p>排序字段必须由各接口显式声明白名单（字段名 → 实体 getter），这里只按白名单取列，
 * 任何情况下都不把入参当列名拼进 SQL，因此不存在 order by 注入面。
 */
public final class PageSortSupport {

    private static final String ASC = "asc";

    private static final String DESC = "desc";

    private PageSortSupport() {
    }

    /**
     * 按查询参数里的 sortField/sortOrder 追加排序。
     *
     * @param sortable     本接口允许排序的字段白名单,键为前端字段名
     * @param stableColumn 次级排序列(通常是主键):排序列往往不唯一,不兜底会导致翻页重复或漏行
     * @return true 表示已应用动态排序,调用方不要再追加默认排序;false 表示未传 sortField,调用方沿用各自的默认排序
     */
    public static <T> boolean apply(LambdaQueryWrapper<T> wrapper,
                                    PageQuery query,
                                    Map<String, SFunction<T, ?>> sortable,
                                    SFunction<T, ?> stableColumn) {
        String field = trimToNull(query.getSortField());
        if (field == null) {
            return false;
        }
        SFunction<T, ?> column = sortable.get(field);
        if (column == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "不支持的排序字段：" + field + "，可选值：" + String.join("、", new TreeSet<>(sortable.keySet())));
        }
        boolean ascending = isAscending(query.getSortOrder());
        wrapper.orderBy(true, ascending, column);
        wrapper.orderBy(true, true, stableColumn);
        return true;
    }

    private static boolean isAscending(String sortOrder) {
        String order = trimToNull(sortOrder);
        if (order == null || ASC.equalsIgnoreCase(order)) {
            return true;
        }
        if (DESC.equalsIgnoreCase(order)) {
            return false;
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "排序方向只支持 asc 或 desc，当前值：" + order);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
