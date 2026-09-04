package com.yqz.openblog.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 设备令牌签发配置（openblog.device-token，env OPENBLOG_DEVICE_TOKEN_SECRET 注入）。
 */
@ConfigurationProperties(prefix = "openblog.device-token")
public class DeviceTokenProperties {

    /**
     * 签发 HMAC 密钥。缺省或长度不足时 {@code DeviceTokenService} 构造器 fail-fast（与 JwtVerifier 同策略），
     * 绝不回退公开默认密钥——否则攻击者可自签令牌换取任意"新设备身份"，第 2 层防构造归零。
     */
    private String secret;

    /**
     * 令牌有效期（秒），默认 7 天。
     */
    private long ttlSeconds = 604_800L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
