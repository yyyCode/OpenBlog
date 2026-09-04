package com.yqz.openblog.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.gateway.config.DeviceTokenProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTokenMintFilterTest {

    private static final String SECRET = "device-token-test-secret-key-0123456789abcdef";

    private DeviceTokenMintFilter newFilter() {
        DeviceTokenProperties props = new DeviceTokenProperties();
        props.setSecret(SECRET);
        return new DeviceTokenMintFilter(new DeviceTokenService(props), new ObjectMapper());
    }

    @Test
    void postMintPath_returnsTokenAndShortCircuits() {
        AtomicBoolean chained = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chained.set(true);
            return Mono.empty();
        };
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/devices/token").build());

        newFilter().filter(ex, chain).block();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chained).isFalse(); // 本地应答，不转发 business
        String body = ex.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":0");
        assertThat(body).contains("\"data\":{");
        assertThat(body).contains("\"token\":\"");
        assertThat(body).contains("\"expiresAt\":");
        assertThat(body).doesNotContain("\"code\":4290");
    }

    @Test
    void getMintPath_passesThrough() {
        AtomicBoolean chained = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chained.set(true);
            return Mono.empty();
        };
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/devices/token").build());

        newFilter().filter(ex, chain).block();

        assertThat(chained).isTrue();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void otherPath_passesThrough() {
        AtomicBoolean chained = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chained.set(true);
            return Mono.empty();
        };
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());

        newFilter().filter(ex, chain).block();

        assertThat(chained).isTrue();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }
}
