package com.yqz.openblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词过滤配置。
 */
@ConfigurationProperties(prefix = "openblog.sensitive-word")
public class SensitiveWordProperties {

    /**
     * 白名单：命中内置词库但属于误伤的词，在此逐条豁免（词库是通用词表，误伤只能靠这里兜）。
     * 在启动时并入 DFA，改动需重启生效。
     */
    private List<String> allowWords = new ArrayList<>();

    public List<String> getAllowWords() {
        return allowWords;
    }

    public void setAllowWords(List<String> allowWords) {
        this.allowWords = allowWords;
    }
}
