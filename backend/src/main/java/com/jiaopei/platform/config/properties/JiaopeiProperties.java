package com.jiaopei.platform.config.properties;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jiaopei")
public class JiaopeiProperties {

    private final Security security = new Security();
    private final Modules modules = new Modules();
    private final Auth auth = new Auth();

    public Security getSecurity() {
        return security;
    }

    public Modules getModules() {
        return modules;
    }

    public Auth getAuth() {
        return auth;
    }

    public static class Security {

        private List<String> publicPaths = new ArrayList<>();

        public List<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(List<String> publicPaths) {
            this.publicPaths = publicPaths;
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
}
