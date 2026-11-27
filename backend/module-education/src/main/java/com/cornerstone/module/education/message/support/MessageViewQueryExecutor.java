package com.cornerstone.module.education.message.support;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.config.MybatisPlusConfig;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 只读视图查询执行器。
 *
 * <p>视图名来自 jp_msg_source 白名单，并且必须匹配 {@code v_msg_[A-Za-z0-9_]+} 才允许拼进 SQL；
 * 前端没有任何入口能传表名或 SQL。查询带 30 秒超时与行数上限，超限时标记 truncated，
 * 巡检只在「成功且未截断」时才做自动消解，避免把没扫到的历史事件误判为已解决。</p>
 */
@Component
public class MessageViewQueryExecutor {

    /** 单次巡检最多处理的行数；超限只记录告警并跳过消解，避免一次全表扫描拖垮业务库。 */
    public static final int MAX_ROWS = 5000;

    private static final Pattern SAFE_VIEW = Pattern.compile("v_msg_[A-Za-z0-9_]+");
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private final JdbcTemplate jdbcTemplate;

    public MessageViewQueryExecutor(@Qualifier(MybatisPlusConfig.PRIMARY_DS) DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        this.jdbcTemplate.setMaxRows(MAX_ROWS + 1);
    }

    public ScanResult scan(String viewName) {
        String safeView = requireView(viewName);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + safeView);
        if (rows.size() > MAX_ROWS) {
            return new ScanResult(new ArrayList<>(rows.subList(0, MAX_ROWS)), true);
        }
        return new ScanResult(rows, false);
    }

    /** 读取视图列名，用于保存类型/校验模板时给运维明确反馈。 */
    public List<String> columns(String viewName) {
        String safeView = requireView(viewName);
        List<String> columns = jdbcTemplate.query(
            "SELECT * FROM " + safeView + " LIMIT 0",
            (ResultSetExtractor<List<String>>) resultSet -> {
                ResultSetMetaData metaData = resultSet.getMetaData();
                List<String> labels = new ArrayList<>();
                for (int index = 1; index <= metaData.getColumnCount(); index++) {
                    labels.add(metaData.getColumnLabel(index));
                }
                return labels;
            });
        return columns == null ? List.of() : columns;
    }

    private String requireView(String viewName) {
        String trimmed = viewName == null ? "" : viewName.trim();
        if (!SAFE_VIEW.matcher(trimmed).matches()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "数据源视图名不合法，只允许 v_msg_ 开头的字母/数字/下划线：" + viewName);
        }
        return trimmed;
    }

    /**
     * @param rows      本次扫描命中的候选行（最多 {@link #MAX_ROWS} 行）
     * @param truncated 是否因超过行数上限被截断；true 时调用方不得自动消解历史事件
     */
    public record ScanResult(List<Map<String, Object>> rows, boolean truncated) {
    }
}
