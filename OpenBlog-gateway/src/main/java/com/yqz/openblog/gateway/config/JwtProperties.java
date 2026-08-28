package com.yqz.openblog.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 与 business 同源的 JWT 密钥配置（openblog.jwt.secret，env OPENBLOG_JWT_SECRET 注入同一值）。 */
@ConfigurationProperties(prefix = "openblog.jwt")
public class JwtProperties {

    private String secret;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
