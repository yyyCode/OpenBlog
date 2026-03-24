package com.yqz.openblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 站点展示信息（前端侧栏等）。
 */
@ConfigurationProperties(prefix = "openblog.site")
public class SiteProperties {

    /**
     * 博客站点版本号（与单条更新日志的 versionLabel 可独立维护）。
     */
    private String version = "1.0.0";

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
