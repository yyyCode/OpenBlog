package com.yqz.openblog.gateway.filter;

import com.yqz.openblog.gateway.config.DeviceTokenProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * 设备令牌签发与解析（设备指纹防构造第 2 层的核心）。
 * <p>
 * 目的：把「客户端自报的设备指纹 header」从唯一的身份输入，升级为「网关私钥签发、签发端点按 IP
 * 限流的授权身份」。令牌内只含每次签发随机生成的 deviceId（UUID）——限流桶身份 = deviceId，
 * 客户端指纹仅作附带信号（第 1 层守卫 + 日志 + business 侧账号×设备绑定）。随机 deviceId 意味着
 * 换新身份必须逐个调签发端点（不再免费）；旧令牌也不携带指纹，指纹漂移不误伤。
 * <p>
 * 密钥守卫与 JwtVerifier 同策略：缺省/长度 &lt; 32 直接 fail-fast，绝不回退默认密钥。
 * 解析（验签/过期/坏签名）失败一律返回 null，调用方降级纯 IP——fail 侧安全，不拦真实用户。
 */
@Component
public class DeviceTokenService {

    private final SecretKey key;
    private final JwtParser parser;
    private final long ttlSeconds;

    public DeviceTokenService(DeviceTokenProperties props) {
        String secret = props.getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("openblog.device-token.secret 长度须至少 32 字符（防自签伪造）");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(key).build();
        this.ttlSeconds = props.getTtlSeconds();
    }

    /** 签发一个设备令牌（每次随机 deviceId）。 */
    public IssuedToken mint() {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        String token = Jwts.builder()
                .claim("dev", UUID.randomUUID().toString())
                .expiration(Date.from(expiresAt))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        return new IssuedToken(token, expiresAt.toEpochMilli());
    }

    /**
     * @return 令牌有效时返回其中随机 deviceId；缺失/无效/过期/坏签名一律返回 null
     */
    public String deviceIdOf(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Object dev = claims.get("dev");
            return dev instanceof String s ? s : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 签发结果：令牌 + 服务端口径的过期时刻（epoch 毫秒），客户端据此本地判断何时续签。 */
    public static final class IssuedToken {
        private final String token;
        private final long expiresAtMs;

        IssuedToken(String token, long expiresAtMs) {
            this.token = token;
            this.expiresAtMs = expiresAtMs;
        }

        public String getToken() {
            return token;
        }

        public long getExpiresAtMs() {
            return expiresAtMs;
        }
    }
}
