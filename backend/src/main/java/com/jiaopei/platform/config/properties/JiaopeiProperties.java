package com.jiaopei.platform.config.properties;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jiaopei")
public class JiaopeiProperties {

    private final Security security = new Security();
    private final Modules modules = new Modules();

    public Security getSecurity() {
        return security;
    }

    public Modules getModules() {
        return modules;
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
}
