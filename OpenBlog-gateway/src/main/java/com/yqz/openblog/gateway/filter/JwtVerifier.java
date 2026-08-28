package com.yqz.openblog.gateway.filter;

import com.yqz.openblog.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 校验（与 business JwtService 同源：HS256 + openblog.jwt.secret）。
 * 只做「签名 + 过期」粗校验并取 uid；角色授权仍由 business 完成。
 */
@Component
public class JwtVerifier {

    private final SecretKey key;
    private final JwtParser parser;

    public JwtVerifier(JwtProperties props) {
        String secret = props.getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("openblog.jwt.secret 长度须至少 32 字符（与 business 同源）");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(key).build();
    }

    /** @return 解析出的 userId；token 缺失/无效/过期返回 null */
    public Long parseUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Object uid = claims.get("uid");
            if (uid instanceof Integer i) {
                return i.longValue();
            }
            if (uid instanceof Long l) {
                return l;
            }
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
