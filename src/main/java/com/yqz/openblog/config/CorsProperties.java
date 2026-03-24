package com.yqz.openblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS 允许来源（生产环境请收紧为前端域名）。
 */
@ConfigurationProperties(prefix = "openblog.cors")
public class CorsProperties {

    /**
     * 与 Spring {@link org.springframework.web.cors.CorsConfiguration#setAllowedOriginPatterns} 一致。
     */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }
}
