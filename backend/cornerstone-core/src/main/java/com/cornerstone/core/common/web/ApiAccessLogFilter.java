package com.cornerstone.core.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 接口访问日志：一次 API 调用一行，排障时先看这里，再用链路 ID 翻业务日志与操作日志。
 *
 * <p>打 INFO 的范围：非 GET（写操作）与所有 4xx/5xx；成功的 GET 只打 DEBUG，
 * 否则下拉框、小程序轮询会把日志刷满。链路 ID 由 {@link TraceIdFilter} 写入 MDC，
 * 日志 pattern 统一带出，这里不重复拼；本过滤器排在 TraceIdFilter 之后。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessLogFilter.class);

    /** 健康检查/接口文档/错误派发不是业务调用，记下来只有噪音。 */
    private static final String[] SKIPPED_PREFIXES = {"/api/health", "/v3/api-docs", "/swagger-ui", "/error"};

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : SKIPPED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            int status = response.getStatus();
            String line = "{} {}{} -> {} ({}ms)";
            Object[] args = {request.getMethod(), request.getRequestURI(), query(request), status, durationMs};
            if (!"GET".equalsIgnoreCase(request.getMethod()) || status >= 400) {
                log.info(line, args);
            } else if (log.isDebugEnabled()) {
                log.debug(line, args);
            }
        }
    }

    private String query(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isBlank() ? "" : "?" + query;
    }
}
