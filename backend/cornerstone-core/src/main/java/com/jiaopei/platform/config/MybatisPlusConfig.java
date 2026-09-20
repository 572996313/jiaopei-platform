package com.jiaopei.platform.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 业务主数据源与 MyBatis-Plus 配置。
 *
 * <p>日志库引入第二个数据源(AuditDataSourceConfig)后,Spring Boot 的
 * DataSourceAutoConfiguration / MybatisPlusAutoConfiguration 因容器中出现
 * 多个 DataSource 而自动退避,主 DataSource、SqlSessionFactory、事务管理器
 * 必须在此显式声明,并以 @Primary 标记为业务默认。</p>
 */
@Configuration
@MapperScan(
    basePackages = {
        "com.jiaopei.platform.auth.mapper",
        "com.jiaopei.platform.system.mapper",
        "com.jiaopei.platform.tenant.mapper"
    },
    sqlSessionFactoryRef = "primarySqlSessionFactory",
    sqlSessionTemplateRef = "primarySqlSessionTemplate")
public class MybatisPlusConfig {

    private static final Logger log = LoggerFactory.getLogger(MybatisPlusConfig.class);

    public static final String PRIMARY_DS = "primaryDataSource";
    public static final String PRIMARY_FACTORY = "primarySqlSessionFactory";
    public static final String PRIMARY_SESSION = "primarySqlSessionTemplate";
    public static final String PRIMARY_TX = "primaryTransactionManager";

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${jiaopei.flyway.locations:classpath:db/migration}")
    private List<String> flywayLocations;

    /**
     * 主业务数据源(Hikari,基于 spring.datasource.* 配置)。
     * 原先由 Spring Boot 自动配置提供;引入多数据源后自动配置退避,这里显式建档并命名为
     * primaryDataSource,audit 数据源(com.jiaopei.platform.config.AuditDataSourceConfig)与之隔离。
     */
    @Bean(name = PRIMARY_DS, destroyMethod = "close")
    @Primary
    public DataSource primaryDataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driverClassName);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("jiaopei-primary-pool");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }

    /** 主 MyBatis-Plus SqlSessionFactory,业务 Mapper 绑定到此 factory。 */
    @Bean(name = PRIMARY_FACTORY)
    @Primary
    public SqlSessionFactory primarySqlSessionFactory(
        @Qualifier(PRIMARY_DS) DataSource primaryDataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(primaryDataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setPlugins(mybatisPlusInterceptor());
        factory.afterPropertiesSet();
        return factory.getObject();
    }

    /** 主 SqlSessionTemplate。 */
    @Bean(name = PRIMARY_SESSION)
    @Primary
    public SqlSessionTemplate primarySqlSessionTemplate(
        @Qualifier(PRIMARY_FACTORY) SqlSessionFactory primarySqlSessionFactory) {
        return new SqlSessionTemplate(primarySqlSessionFactory);
    }

    /** 主事务管理器:业务 @Transactional 走主库。 */
    @Bean(name = PRIMARY_TX)
    @Primary
    public PlatformTransactionManager primaryTransactionManager(
        @Qualifier(PRIMARY_DS) DataSource primaryDataSource) {
        return new DataSourceTransactionManager(primaryDataSource);
    }

    /**
     * 业务库主 Flyway。引入日志库后 Spring Boot 主 Flyway 自动配置退避,
     * 这里显式声明并绑定主数据源。
     *
     * <p>迁移目录由 {@code jiaopei.flyway.locations} 拼接:存量 V1~V38 在 app 模块的
     * {@code classpath:db/migration},今后某模块要自带脚本时,在该配置项后追加模块目录即可,
     * 不需要改这里的代码。开关关掉的模块其 jar 不在 classpath,对应目录自然缺失,Flyway 会忽略。</p>
     */
    @Bean(name = "primaryFlyway", initMethod = "migrate")
    public Flyway primaryFlyway(@Qualifier(PRIMARY_DS) DataSource primaryDataSource) {
        return Flyway.configure()
            .dataSource(primaryDataSource)
            .locations(flywayLocations.toArray(String[]::new))
            .baselineOnMigrate(true)
            .load();
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @PostConstruct
    void logDataSource() {
        log.info("业务主数据源已显式配置:{}", url);
    }
}