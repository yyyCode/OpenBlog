package com.yqz.openblog.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Elasticsearch 搜索相关配置参数。
 */
@ConfigurationProperties(prefix = "openblog.search")
public class SearchProperties {

    /**
     * 是否启用 ES 搜索，默认 false（未配置 ES 时不影响启动）。
     */
    private boolean enabled = false;

    /**
     * ES 集群地址，逗号分隔。
     */
    private String uris = "http://localhost:9200";

    /**
     * ES 用户名（可选）。
     */
    private String username;

    /**
     * ES 密码（可选）。
     */
    private String password;

    /**
     * 索引名前缀，实际索引名 = 前缀 + 业务名。
     */
    private String indexPrefix = "openblog_";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUris() {
        return uris;
    }

    public void setUris(String uris) {
        this.uris = uris;
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

    public String getIndexPrefix() {
        return indexPrefix;
    }

    public void setIndexPrefix(String indexPrefix) {
        this.indexPrefix = indexPrefix;
    }
}
