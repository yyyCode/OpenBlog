package com.yqz.openblog.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.gateway.config.GatewayProperties;
import com.yqz.openblog.gateway.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCheckFilterTest {

    private static final String SECRET = "test-secret-key-0123456789abcdef-0123456789";

    private JwtCheckFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret(SECRET);
        JwtVerifier verifier = new JwtVerifier(jwtProps);

        GatewayProperties props = new GatewayProperties();
        props.getAuth().setSkipPaths(List.of("/api/v1/auth/login"));
        filter = new JwtCheckFilter(props, verifier, new ObjectMapper());
    }

    private String signToken(long uid) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().claim("uid", uid)
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    private MockServerWebExchange exchange(String path, String bearer) {
        var req = MockServerHttpRequest.get(path);
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return MockServerWebExchange.from(req.build());
    }

    @Test
    void whitelistedPath_passesEvenWithInvalidToken() {
        ServerWebExchange ex = exchange("/api/v1/auth/login", "invalid.token.here");
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void validToken_passes() {
        ServerWebExchange ex = exchange("/api/v1/user/profile", signToken(42L));
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void noTokenOnProtectedPath_passesToBusiness() {
        // 无 token 放行交由 business 判定（匿名公共路由不被网关误伤）
        ServerWebExchange ex = exchange("/api/v1/articles/1", null);
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void invalidTokenOnProtectedPath_returns401() {
        MockServerWebExchange ex = exchange("/api/v1/user/profile", "invalid.token.here");
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("\"code\":4010");
    }

    @Test
    void expiredToken_returns401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder().claim("uid", 7L)
                .setExpiration(new java.util.Date(System.currentTimeMillis() - 60_000))
                .signWith(key, SignatureAlgorithm.HS256).compact();
        ServerWebExchange ex = exchange("/api/v1/user/profile", expired);
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validTokenWithoutUidClaim_returns401() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String noUid = Jwts.builder()
                .signWith(key, SignatureAlgorithm.HS256).compact();
        ServerWebExchange ex = exchange("/api/v1/user/profile", noUid);
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
