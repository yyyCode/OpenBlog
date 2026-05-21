package com.yqz.openblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 滑动验证、失败锁定等安全相关配置。
 */
@ConfigurationProperties(prefix = "openblog.auth-security")
public class AuthSecurityProperties {

    private Slider slider = new Slider();
    private LoginLockout loginLockout = new LoginLockout();

    public Slider getSlider() {
        return slider;
    }

    public void setSlider(Slider slider) {
        this.slider = slider;
    }

    public LoginLockout getLoginLockout() {
        return loginLockout;
    }

    public void setLoginLockout(LoginLockout loginLockout) {
        this.loginLockout = loginLockout;
    }

    public static class Slider {
        /**
         * 是否要求登录/注册前先完成「滑到尽头」验证（Redis 记录一次性凭证）。
         */
        private boolean enabled = false;
        /**
         * challenge / 通过标记有效时间（秒）。
         */
        private int ttlSeconds = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class LoginLockout {
        private boolean enabled = true;
        /**
         * 同一 IP 在窗口内允许的最大失败次数（仅统计密码错误）。
         */
        private int maxFailuresPerIp = 5;
        /**
         * 失败计数滑动窗口（秒）。
         */
        private int failureWindowSeconds = 300;
        /**
         * 触发锁定后的禁止登录时长（秒）。
         */
        private int lockoutSeconds = 900;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxFailuresPerIp() {
            return maxFailuresPerIp;
        }

        public void setMaxFailuresPerIp(int maxFailuresPerIp) {
            this.maxFailuresPerIp = maxFailuresPerIp;
        }

        public int getFailureWindowSeconds() {
            return failureWindowSeconds;
        }

        public void setFailureWindowSeconds(int failureWindowSeconds) {
            this.failureWindowSeconds = failureWindowSeconds;
        }

        public int getLockoutSeconds() {
            return lockoutSeconds;
        }

        public void setLockoutSeconds(int lockoutSeconds) {
            this.lockoutSeconds = lockoutSeconds;
        }
    }
}
