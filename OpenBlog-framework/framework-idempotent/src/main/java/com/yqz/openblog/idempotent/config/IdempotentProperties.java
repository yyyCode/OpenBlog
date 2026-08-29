package com.yqz.openblog.idempotent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openblog.idempotent")
public class IdempotentProperties {

    /** 是否启用幂等组件，默认 true */
    private boolean enabled = true;

    /** Key 环境前缀；留空时自动取当前 active profile */
    private String envPrefix = "";

    /** 本地锁对象缓存时长（小时） */
    private int localLockCacheHours = 48;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEnvPrefix() {
        return envPrefix;
    }

    public void setEnvPrefix(String envPrefix) {
        this.envPrefix = envPrefix;
    }

    public int getLocalLockCacheHours() {
        return localLockCacheHours;
    }

    public void setLocalLockCacheHours(int localLockCacheHours) {
        this.localLockCacheHours = localLockCacheHours;
    }
}
