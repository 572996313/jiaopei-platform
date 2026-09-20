package com.cornerstone.core.config.properties;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jiaopei")
public class JiaopeiProperties {

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
         * 敏感数据加解密密钥(jiaopei.security.data-key),经 SHA-256 派生为 AES-256 密钥。
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

    public static class Modules {

        private boolean organizationEnabled = true;
        private boolean educationEnabled = true;

        public boolean isOrganizationEnabled() {
            return organizationEnabled;
        }

        public void setOrganizationEnabled(boolean organizationEnabled) {
            this.organizationEnabled = organizationEnabled;
        }

        public boolean isEducationEnabled() {
            return educationEnabled;
        }

        public void setEducationEnabled(boolean educationEnabled) {
            this.educationEnabled = educationEnabled;
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
     * 操作日志库(jiaopei_log)连接配置,与业务库同实例、独立 database。
     * 通过 DB_AUDIT_* 环境变量覆盖,默认复用业务库账号(root/密码见 DB_PASSWORD)。
     */
    public static class AuditDb {

        private String host = "127.0.0.1";
        private int port = 3306;
        private String database = "jiaopei_log";
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
