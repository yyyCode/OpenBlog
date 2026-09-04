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
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");
        assertThat(service.deviceIdOf(tampered)).isNull();
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
