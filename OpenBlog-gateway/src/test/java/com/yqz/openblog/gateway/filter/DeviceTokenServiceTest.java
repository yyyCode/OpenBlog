package com.yqz.openblog.gateway.filter;

import com.yqz.openblog.gateway.config.DeviceTokenProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceTokenServiceTest {

    private static final String SECRET = "device-token-test-secret-key-0123456789abcdef";
    private static final String SHORT_SECRET = "too-short";

    private DeviceTokenService newService() {
        DeviceTokenProperties props = new DeviceTokenProperties();
        props.setSecret(SECRET);
        return new DeviceTokenService(props);
    }

    @Test
    void mint_roundTripsDeviceId() {
        DeviceTokenService service = newService();
        String token = service.mint().getToken();
        String dev = service.deviceIdOf(token);
        // UUID（含连字符 36 位），每次签发随机 → 仅作桶身份，不承载指纹语义
        assertThat(dev).matches("^[0-9a-fA-F-]{36}$");
    }

    @Test
    void mint_twoTokensHaveDistinctDeviceIds() {
        DeviceTokenService service = newService();
        String a = service.deviceIdOf(service.mint().getToken());
        String b = service.deviceIdOf(service.mint().getToken());
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void mint_exposesServerSidedExpiry() {
        DeviceTokenService service = newService();
        DeviceTokenService.IssuedToken issued = service.mint();
        assertThat(issued.getExpiresAtMs()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    void deviceIdOf_tamperedToken_returnsNull() {
        DeviceTokenService service = newService();
        String token = service.mint().getToken();
        // 篡改 payload 段（第二个 '.' 之后）任一位：签名段内容被改 → HMAC 比对必然失败。
        // 注意不能只改签名段末尾：末尾 base64url 字符含 0 填充位，翻转低 bit 解码后可能不变。
        int secondDot = token.indexOf('.', token.indexOf('.') + 1);
        StringBuilder sb = new StringBuilder(token);
        char orig = sb.charAt(secondDot + 1);
        sb.setCharAt(secondDot + 1, orig == 'a' ? 'b' : 'a');
        assertThat(service.deviceIdOf(sb.toString())).isNull();
    }

    @Test
    void deviceIdOf_expiredToken_returnsNull() {
        DeviceTokenService service = newService();
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .claim("dev", "deadbeef-deadbeef-deadbeef-deadbeef")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        assertThat(service.deviceIdOf(expired)).isNull();
    }

    @Test
    void deviceIdOf_nonUuidDevClaim_returnsNull() {
        DeviceTokenService service = newService();
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        // 签名有效、未过期但 dev claim 非 UUID 形态 → 拒绝，防非 UUID 字符串被拼进限流 key 段
        String weird = Jwts.builder()
                .claim("dev", "not-a-uuid;:{}_/")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        assertThat(service.deviceIdOf(weird)).isNull();
    }

    @Test
    void deviceIdOf_blankOrNull_returnsNull() {
        DeviceTokenService service = newService();
        assertThat(service.deviceIdOf(null)).isNull();
        assertThat(service.deviceIdOf("")).isNull();
        assertThat(service.deviceIdOf("   ")).isNull();
    }

    @Test
    void constructor_rejectsShortSecret() {
        DeviceTokenProperties props = new DeviceTokenProperties();
        props.setSecret(SHORT_SECRET);
        assertThatThrownBy(() -> new DeviceTokenService(props))
                .isInstanceOf(IllegalStateException.class);
    }
}
