package com.cornerstone.core.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.cornerstone.core.config.properties.CornerstoneProperties;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 操作日志库(jiaopei_log)独立数据源配置。
 *
 * <p>与业务主数据源(MybatisPlusConfig 管理的业务 Mapper)完全隔离:</p>
 * <ul>
 *   <li>独立的 Hikari 数据源,默认复用业务库的 MySQL 实例与账号(DB_AUDIT_* 可单独覆盖);</li>
 *   <li>独立的 MyBatis-Plus SqlSessionFactory(含分页插件);</li>
 *   <li>独立的 Flyway,迁移脚本位于 classpath:db/audit-migration,主 Flyway 不管日志库;</li>
 *   <li>audit Mapper 通过 sqlSessionFactoryRef 指向 auditSqlSessionFactory,与业务 Mapper 分开扫描。</li>
 * </ul>
 *
 * <p>日志库不可用时降级处理:启动阶段迁移失败只告警不阻断业务;写入阶段切面
 * 异步落库异常被隔离,只丢日志、不影响业务事务与接口响应。</p>
 */
@Configuration
@MapperScan(basePackages = "com.cornerstone.core.audit.mapper",
        sqlSessionFactoryRef = "auditSqlSessionFactory",
        sqlSessionTemplateRef = "auditSqlSessionTemplate")
public class AuditDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(AuditDataSourceConfig.class);

    private final CornerstoneProperties cornerstoneProperties;

    public AuditDataSourceConfig(CornerstoneProperties cornerstoneProperties) {
        this.cornerstoneProperties = cornerstoneProperties;
    }

    @Bean(name = "auditDataSource", destroyMethod = "close")
    public DataSource auditDataSource() {
        CornerstoneProperties.AuditDb auditDb = cornerstoneProperties.getAuditDb();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setJdbcUrl("jdbc:mysql://" + auditDb.getHost() + ":" + auditDb.getPort() + "/"
            + auditDb.getDatabase()
            + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false"
            + "&allowPublicKeyRetrieval=true");
        dataSource.setUsername(auditDb.getUsername());
        dataSource.setPassword(auditDb.getPassword());
        // 日志写入为低频异步操作,连接池规模不宜过大
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(0);
        dataSource.setPoolName("cornerstone-audit-pool");
        return dataSource;
    }

    @Bean(name = "auditSqlSessionFactory")
    public SqlSessionFactory auditSqlSessionFactory(
        @Qualifier("auditDataSource") DataSource auditDataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(auditDataSource);
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
            new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setPlugins(auditPaginationInterceptor());
        factory.afterPropertiesSet();
        return factory.getObject();
    }

    @Bean(name = "auditSqlSessionTemplate")
    public SqlSessionTemplate auditSqlSessionTemplate(
        @Qualifier("auditSqlSessionFactory") SqlSessionFactory auditSqlSessionFactory) {
        return new SqlSessionTemplate(auditSqlSessionFactory);
    }

    @Bean
    public MybatisPlusInterceptor auditPaginationInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 日志库迁移(Flyway 第二实例)。方法内执行 migrate 并捕获异常:
     * 日志库不存在/暂不可达仅告警,绝不阻断业务应用启动;
     * 后续写入失败再由异步记录器隔离。
     */
    @Bean(name = "auditFlyway")
    public Flyway auditFlyway(@Qualifier("auditDataSource") DataSource auditDataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(auditDataSource)
            .locations("classpath:db/audit-migration")
            .baselineOnMigrate(true)
            .load();
        try {
            flyway.migrate();
        } catch (Exception e) {
            log.error("jiaopei_log 日志库迁移失败(业务不阻断,日志留痕暂不可用):{}", e.getMessage());
        }
        return flyway;
    }
}