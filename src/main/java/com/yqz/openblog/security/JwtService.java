package com.yqz.openblog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getAccessTokenExpireSeconds());

        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("role", role);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuer(props.getIssuer())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getRefreshTokenExpireSeconds());

        // MVP：加入 jti，避免同一秒内多次生成完全相同的 refreshToken 导致 tokenHash 冲突
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .setIssuer(props.getIssuer())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .setId(jti)
                .claim("uid", userId)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        Object uid = claims.get("uid");
        if (uid instanceof Integer i) {
            return i.longValue();
        }
        if (uid instanceof Long l) {
            return l;
        }
        return null;
    }

    public String getRoleFromAccessToken(String token) {
        Claims claims = parseToken(token);
        Object role = claims.get("role");
        return role == null ? null : role.toString();
    }
}

