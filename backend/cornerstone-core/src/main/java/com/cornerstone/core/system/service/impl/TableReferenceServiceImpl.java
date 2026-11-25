package com.cornerstone.core.system.service.impl;

import com.cornerstone.core.config.MybatisPlusConfig;
import com.cornerstone.core.system.schema.TableReference;
import com.cornerstone.core.system.service.TableReferenceService;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TableReferenceServiceImpl implements TableReferenceService {

    private static final long CACHE_TTL_MILLIS = 60_000L;

    private static final String REFERENCES_SQL = """
        SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME, CONSTRAINT_NAME
        FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = DATABASE()
          AND REFERENCED_TABLE_SCHEMA = DATABASE()
          AND REFERENCED_TABLE_NAME IS NOT NULL
        ORDER BY TABLE_NAME, COLUMN_NAME
        """;

    private final JdbcTemplate jdbcTemplate;

    private volatile List<TableReference> cache;
    private volatile long cacheAt;

    public TableReferenceServiceImpl(
        @Qualifier(MybatisPlusConfig.PRIMARY_DS) DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public List<TableReference> references() {
        long now = System.currentTimeMillis();
        List<TableReference> snapshot = cache;
        if (snapshot != null && now - cacheAt < CACHE_TTL_MILLIS) {
            return snapshot;
        }
        synchronized (this) {
            if (cache != null && System.currentTimeMillis() - cacheAt < CACHE_TTL_MILLIS) {
                return cache;
            }
            List<TableReference> loaded = jdbcTemplate.query(REFERENCES_SQL,
                (resultSet, rowNum) -> new TableReference(
                    resultSet.getString("TABLE_NAME"),
                    resultSet.getString("COLUMN_NAME"),
                    resultSet.getString("REFERENCED_TABLE_NAME"),
                    resultSet.getString("REFERENCED_COLUMN_NAME"),
                    resultSet.getString("CONSTRAINT_NAME")));
            cache = List.copyOf(loaded);
            cacheAt = System.currentTimeMillis();
            return cache;
        }
    }
}
