package com.yqz.openblog.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "openblog.jwt")
public class JwtProperties {
    private String secret;
    private String issuer;
    private long accessTokenExpireSeconds;
    private long refreshTokenExpireSeconds;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTokenExpireSeconds() {
        return accessTokenExpireSeconds;
    }

    public void setAccessTokenExpireSeconds(long accessTokenExpireSeconds) {
        this.accessTokenExpireSeconds = accessTokenExpireSeconds;
    }

    public long getRefreshTokenExpireSeconds() {
        return refreshTokenExpireSeconds;
    }

    public void setRefreshTokenExpireSeconds(long refreshTokenExpireSeconds) {
        this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
    }
}

