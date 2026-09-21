package com.cornerstone.core.config.properties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cornerstone")
public class CornerstoneProperties {

    private final Security security = new Security();
    private final Modules modules = new Modules();
    private final Auth auth = new Auth();
    private final AuditDb auditDb = new AuditDb();

    public Security getSecurity() {
        return security;
    }

    public Modules getModules() {
        return modules;
    }

    public Auth getAuth() {
        return auth;
    }

    public AuditDb getAuditDb() {
        return auditDb;
    }

    public static class Security {

        private List<String> publicPaths = new ArrayList<>();

        /**
         * 敏感数据加解密密钥(cornerstone.security.data-key),经 SHA-256 派生为 AES-256 密钥。
         * 生产环境必须用环境变量 DATA_ENCRYPT_KEY 覆盖,禁止使用 application.yml 中的开发默认值。
         */
        private String dataKey = "";

        public List<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(List<String> publicPaths) {
            this.publicPaths = publicPaths;
        }

        public String getDataKey() {
            return dataKey;
        }

        public void setDataKey(String dataKey) {
            this.dataKey = dataKey;
        }
    }

    /**
     * 业务模块开关集合：{@code cornerstone.modules.enabled.<模块码>=false} 关闭对应模块。
     *
     * <p>底座只存"码 → 开关"的映射，不认识任何具体模块（阶段五-1 回灌：原来是
     * {@code organizationEnabled}/{@code educationEnabled} 两个写死字段，等于底座记住了教培的模块名）。
     * 未配置视为开启，与各模块 {@code @ConditionalOnProperty(matchIfMissing = true)} 的口径一致。</p>
     */
    public static class Modules {

        private final Map<String, Boolean> enabled = new LinkedHashMap<>();

        public Map<String, Boolean> getEnabled() {
            return enabled;
        }

        public boolean isEnabled(String moduleCode) {
            return enabled.getOrDefault(moduleCode, true);
        }
    }

    public static class Auth {

        private final Jwt jwt = new Jwt();

        public Jwt getJwt() {
            return jwt;
        }
    }

    public static class Jwt {

        /**
         * HS256 签名密钥,长度不得少于 32 字节。
         * 生产环境必须通过 AUTH_JWT_SECRET 环境变量覆盖,禁止使用 application.yml 中的开发默认值。
         */
        private String secret;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    /**
     * 操作日志库连接配置,与业务库同实例、独立 database。
     * 库名属各项目自己的部署标识,底座只给中性默认值,部署方用 DB_AUDIT_NAME / cornerstone.audit-db.database 覆盖。
     * 通过 DB_AUDIT_* 环境变量覆盖,默认复用业务库账号(root/密码见 DB_PASSWORD)。
     */
    public static class AuditDb {

        private String host = "127.0.0.1";
        private int port = 3306;
        private String database = "cornerstone_log";
        private String username = "root";
        private String password = "";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
