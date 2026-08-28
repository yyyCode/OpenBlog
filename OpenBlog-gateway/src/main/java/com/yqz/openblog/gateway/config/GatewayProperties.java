package com.yqz.openblog.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** 网关自定义配置：认证白名单 + 限流规则（openblog.gateway.*）。 */
@ConfigurationProperties(prefix = "openblog.gateway")
public class GatewayProperties {

    private Auth auth = new Auth();
    private RateLimit rateLimit = new RateLimit();

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class Auth {
        private List<String> skipPaths = new ArrayList<>();

        public List<String> getSkipPaths() {
            return skipPaths;
        }

        public void setSkipPaths(List<String> skipPaths) {
            this.skipPaths = skipPaths;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private List<Rule> rules = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<Rule> getRules() {
            return rules;
        }

        public void setRules(List<Rule> rules) {
            this.rules = rules;
        }
    }

    public static class Rule {
        private String path;
        private long windowMs = 60_000;
        private int limit = 60;
        private Scope scope = Scope.IP;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public long getWindowMs() {
            return windowMs;
        }

        public void setWindowMs(long windowMs) {
            this.windowMs = windowMs;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public Scope getScope() {
            return scope;
        }

        public void setScope(Scope scope) {
            this.scope = scope;
        }
    }

    public enum Scope {
        IP, IP_UID
    }
}
