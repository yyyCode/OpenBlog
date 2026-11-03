package com.yqz.openblog.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 搜索框架配置。
 *
 * <p>ES 连接信息走 Spring Boot 官方的 {@code spring.elasticsearch.*}，本类只保留框架行为开关。</p>
 */
@ConfigurationProperties(prefix = "openblog.search")
public class SearchProperties {

    /**
     * 是否启用 ES 搜索，默认 false（未配置 ES 时不影响启动）。
     */
    private boolean enabled = false;

    /**
     * 索引名前缀，实际索引名 = 前缀 + 业务名。
     */
    private String indexPrefix = "openblog_";

    /**
     * 启动后是否自动创建索引并补齐 mapping。
     */
    private boolean autoCreateIndex = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIndexPrefix() {
        return indexPrefix;
    }

    public void setIndexPrefix(String indexPrefix) {
        this.indexPrefix = indexPrefix;
    }

    public boolean isAutoCreateIndex() {
        return autoCreateIndex;
    }

    public void setAutoCreateIndex(boolean autoCreateIndex) {
        this.autoCreateIndex = autoCreateIndex;
    }
}
