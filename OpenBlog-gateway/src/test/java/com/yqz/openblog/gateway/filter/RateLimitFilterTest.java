package com.yqz.openblog.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.gateway.config.DeviceTokenProperties;
import com.yqz.openblog.gateway.config.GatewayProperties;
import com.yqz.openblog.gateway.config.JwtProperties;
import com.yqz.openblog.redis.limiter.SlidingWindowLimiter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private static final String SECRET = "test-secret-key-0123456789abcdef-0123456789";

    private GatewayProperties props;
    private SlidingWindowLimiter limiter;
    private RateLimitFilter filter;
    private DeviceTokenService tokenService;

    @BeforeEach
    void setUp() {
        props = new GatewayProperties();
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret(SECRET);
        DeviceTokenProperties devProps = new DeviceTokenProperties();
        devProps.setSecret(SECRET);
        tokenService = new DeviceTokenService(devProps);
        limiter = mock(SlidingWindowLimiter.class);
        filter = new RateLimitFilter(props, limiter, new JwtVerifier(jwtProps), new ObjectMapper(),
                new FakeGuard(false), tokenService);
    }

    /** 测试替身：直接指定守卫判定，避免 mock StringRedisTemplate.execute 的 varargs 重载歧义。 */
    private static final class FakeGuard extends FingerprintRotationGuard {
        private final boolean over;

        FakeGuard(boolean over) {
            super(mock(StringRedisTemplate.class));
            this.over = over;
        }

        @Override
        public boolean isOverBudget(String ip, String fp, long windowMs, int budget) {
            return over;
        }
    }

    /** 合法设备指纹：32 位 hex（FingerprintJS visitorId 形态）。 */
    private static final String FP_VALID = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6";

    private MockServerWebExchange exchangeWithFp(String path, String fp) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header("X-Forwarded-For", "1.2.3.4")
                        .header("X-Device-Fingerprint", fp)
                        .build());
    }

    private void addRule(String path, int limit, GatewayProperties.Scope scope) {
        GatewayProperties.Rule rule = new GatewayProperties.Rule();
        rule.setPath(path);
        rule.setWindowMs(60_000);
        rule.setLimit(limit);
        rule.setScope(scope);
        props.getRateLimit().getRules().add(rule);
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header("X-Forwarded-For", "1.2.3.4").build());
    }

    private MockServerWebExchange exchangeWithRealIp(String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header("X-Forwarded-For", "1.2.3.4")
                        .header("X-Real-IP", "9.9.9.9")
                        .build());
    }

    private MockServerWebExchange exchangeWithToken(String path, String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header("X-Forwarded-For", "1.2.3.4")
                        .header("Authorization", "Bearer " + token)
                        .build());
    }

    private String signToken(long uid) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().claim("uid", uid)
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    @Test
    void allowedRequest_passes() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        ServerWebExchange ex = exchange("/api/v1/auth/login");
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void overLimit_returns429() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(false);
        MockServerWebExchange ex = exchange("/api/v1/auth/login");
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("\"code\":4290");
    }

    @Test
    void disabled_passesWithoutCallingLimiter() {
        props.getRateLimit().setEnabled(false);
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        ServerWebExchange ex = exchange("/api/v1/auth/login");
        filter.filter(ex, c -> Mono.empty()).block();
        verify(limiter, never()).tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void unmatchedPath_passesWithoutCallingLimiter() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        ServerWebExchange ex = exchange("/api/v1/articles/1");
        filter.filter(ex, c -> Mono.empty()).block();
        verify(limiter, never()).tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void xRealIpPreferredOverXff() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        ServerWebExchange ex = exchangeWithRealIp("/api/v1/auth/login");
        filter.filter(ex, c -> Mono.empty()).block();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).contains("9.9.9.9").doesNotContain("1.2.3.4");
    }

    @Test
    void ipUidScope_usesUidFromValidToken() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP_UID);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        ServerWebExchange ex = exchangeWithToken("/api/v1/auth/login", signToken(42L));
        filter.filter(ex, c -> Mono.empty()).block();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).contains("_42_");
    }

    @Test
    void ipUidScope_degradesToIpWithoutToken() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP_UID);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        ServerWebExchange ex = exchange("/api/v1/auth/login");
        filter.filter(ex, c -> Mono.empty()).block();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo("gateway:rl:1.2.3.4_/api/v1/auth/login");
    }

    @Test
    void fpIpScope_withoutDeviceToken_ignoresFingerprint_usesPureIp() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        // 第 2 层语义：裸指纹（格式合法但无网关签发令牌）不再换取独立桶 → 纯 IP，防"每请求换 fp"绕过
        ServerWebExchange ex = exchangeWithFp("/api/v1/auth/login", FP_VALID);
        filter.filter(ex, c -> Mono.empty()).block();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo("gateway:rl:1.2.3.4_/api/v1/auth/login");
    }

    @Test
    void fpIpScope_withValidDeviceToken_usesDeviceIdNotFingerprint() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        String[] issued = mintDeviceToken();
        ServerWebExchange ex = exchangeWithDeviceToken("/api/v1/auth/login", issued[0], FP_VALID);
        filter.filter(ex, c -> Mono.empty()).block();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
        // 桶身份 = 令牌内随机 deviceId，不含客户端自报指纹
        assertThat(keyCaptor.getValue())
                .isEqualTo("gateway:rl:" + issued[1] + "_1.2.3.4_/api/v1/auth/login")
                .doesNotContain(FP_VALID);
    }

    @Test
    void fpIpScope_withInvalidDeviceToken_degradesToIp() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        // 伪造/乱写的令牌验签不过 → deviceIdOf 返回 null → 纯 IP
        ServerWebExchange ex = exchangeWithDeviceToken("/api/v1/auth/login", "not-a-valid-device-token", FP_VALID);
        filter.filter(ex, c -> Mono.empty()).block();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo("gateway:rl:1.2.3.4_/api/v1/auth/login");
    }

    @Test
    void fpIpScope_withOverlongDeviceToken_degradesToIp() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        // 超长（>512）令牌头 → 长度前置校验截断，视为无令牌，纯 IP（不进入 JWT 解析）
        ServerWebExchange ex = exchangeWithDeviceToken("/api/v1/auth/login", "x".repeat(600), FP_VALID);
        filter.filter(ex, c -> Mono.empty()).block();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo("gateway:rl:1.2.3.4_/api/v1/auth/login");
    }

    @Test
    void fpIpScope_withExpiredDeviceToken_degradesToIp() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        // 已过期但签名的令牌（构造于 60s 前）→ 视为无令牌，纯 IP
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .claim("dev", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        ServerWebExchange ex = exchangeWithDeviceToken("/api/v1/auth/login", expired, FP_VALID);
        filter.filter(ex, c -> Mono.empty()).block();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo("gateway:rl:1.2.3.4_/api/v1/auth/login");
    }

    @Test
    void fpIpScope_degradesToIpWithoutFingerprint() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        ServerWebExchange ex = exchange("/api/v1/auth/login"); // 无指纹头
        filter.filter(ex, c -> Mono.empty()).block();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo("gateway:rl:1.2.3.4_/api/v1/auth/login");
    }

    @Test
    void fpIpScope_degradesToIpOnInvalidFingerprint() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        String[] invalidFps = {
                "",      // 空
                "   ",   // 空白
                "short", // 过短（<16）
                // 超长（72 字符 >64）
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6",
                // 含非法字符（正则仅允许字母数字-，`;` 拒绝）
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6;",
                // 含点号（避免指纹长得像 IP 的语义歧义）
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4.123"
        };
        for (String fp : invalidFps) {
            clearInvocations(limiter); // 每轮独立验证，避免累计调用次数干扰
            MockServerWebExchange ex = exchangeWithFp("/api/v1/auth/login", fp);
            filter.filter(ex, c -> Mono.empty()).block();
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
            assertThat(keyCaptor.getValue()).isEqualTo("gateway:rl:1.2.3.4_/api/v1/auth/login");
        }
    }

    @Test
    void redisError_failsOpen() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenThrow(new RuntimeException("redis down"));
        ServerWebExchange ex = exchange("/api/v1/auth/login");
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void nullPathRule_skipped() {
        props.getRateLimit().getRules().add(new GatewayProperties.Rule());
        ServerWebExchange ex = exchange("/api/v1/articles/1");
        filter.filter(ex, c -> Mono.empty()).block();
        verify(limiter, never()).tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    private RateLimitFilter newFilterWithGuard(FingerprintRotationGuard guard) {
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret(SECRET);
        return new RateLimitFilter(props, limiter, new JwtVerifier(jwtProps), new ObjectMapper(),
                guard, tokenService);
    }

    /** 附 X-Device-Token（设备令牌）+ X-Device-Fingerprint 的请求。 */
    private MockServerWebExchange exchangeWithDeviceToken(String path, String deviceToken, String fp) {
        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.get(path).header("X-Forwarded-For", "1.2.3.4");
        if (deviceToken != null) {
            builder.header("X-Device-Token", deviceToken);
        }
        if (fp != null) {
            builder.header("X-Device-Fingerprint", fp);
        }
        return MockServerWebExchange.from(builder.build());
    }

    /** 用与过滤器同源的密钥签发一个合法设备令牌，返回 [token, deviceId]。 */
    private String[] mintDeviceToken() {
        DeviceTokenService.IssuedToken issued = tokenService.mint();
        return new String[]{issued.getToken(), tokenService.deviceIdOf(issued.getToken())};
    }

    @Test
    void fingerprintRotation_overBudget_returns429WithoutCounting() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        RateLimitFilter blocking = newFilterWithGuard(new FakeGuard(true));
        MockServerWebExchange ex = exchangeWithFp("/api/v1/auth/login", FP_VALID);
        blocking.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("\"code\":4290");
        // 疑似轮换：直接拒绝，不进 FP_IP 桶计数
        verify(limiter, never()).tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString());
    }

    @Test
    void fingerprintRotation_withinBudget_proceedsToBucket() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        // 守卫放行 → 回落限流桶；无令牌时按第 2 层语义为纯 IP 桶
        MockServerWebExchange ex = exchangeWithFp("/api/v1/auth/login", FP_VALID);
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo("gateway:rl:1.2.3.4_/api/v1/auth/login");
    }

    @Test
    void fingerprintRotation_ignoredForNonFpScope() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        RateLimitFilter blocking = newFilterWithGuard(new FakeGuard(true));
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        MockServerWebExchange ex = exchange("/api/v1/auth/login");
        blocking.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void fingerprintRotation_redisUnavailable_failsOpen() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        // mock 模板 execute 默认返回 null → 守卫判放行（fail-open），回落 FP_IP 桶
        RateLimitFilter realGuard = newFilterWithGuard(
                new FingerprintRotationGuard(mock(StringRedisTemplate.class)));
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        MockServerWebExchange ex = exchangeWithFp("/api/v1/auth/login", FP_VALID);
        realGuard.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
        verify(limiter).tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString());
    }
}
