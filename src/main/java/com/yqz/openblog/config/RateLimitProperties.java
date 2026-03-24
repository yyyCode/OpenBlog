package com.yqz.openblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全站按 IP 限流（滑动窗口，Redis）。
 */
@ConfigurationProperties(prefix = "openblog.rate-limit")
public class RateLimitProperties {

    /**
     * 是否启用。
     */
    private boolean enabled = true;

    /**
     * 滑动窗口长度（毫秒）。
     */
    private long windowMs = 60_000L;

    /**
     * 每个 IP 在窗口内最多允许的请求次数。
     */
    private int maxRequests = 120;

    /**
     * 针对登录、注册、刷新令牌等接口的单独限流（通常比全站更严）。
     */
    private Auth auth = new Auth();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getWindowMs() {
        return windowMs;
    }

    public void setWindowMs(long windowMs) {
        this.windowMs = windowMs;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public static class Auth {
        private boolean enabled = true;
        private long windowMs = 60_000L;
        private int maxRequests = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getWindowMs() {
            return windowMs;
        }

        public void setWindowMs(long windowMs) {
            this.windowMs = windowMs;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }
    }
}
