package com.yqz.openblog.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorHandlerTest {

    private final GatewayErrorHandler handler = new GatewayErrorHandler(new ObjectMapper());

    private MockServerWebExchange exchange(String traceId) {
        var req = MockServerHttpRequest.get("/api/v1/x");
        return MockServerWebExchange.from(
                traceId == null ? req.build() : req.header("X-Trace-Id", traceId).build());
    }

    @Test
    void downstreamConnectFailure_mapsTo502AndCarriesTraceId() {
        MockServerWebExchange ex = exchange("t-9");
        handler.handle(ex, new ConnectException("connection refused")).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        String body = ex.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":5000").contains("\"message\":\"服务暂不可用\"")
                .contains("\"traceId\":\"t-9\"");
    }

    @Test
    void responseStatus401_mapsTo401() {
        MockServerWebExchange ex = exchange(null);
        handler.handle(ex, new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no auth")).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("\"code\":4010");
    }

    @Test
    void genericException_mapsTo500NoStackLeak() {
        MockServerWebExchange ex = exchange(null);
        handler.handle(ex, new IllegalStateException("boom: secret=abc")).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.getResponse().getBodyAsString().block())
                .contains("\"code\":5000").doesNotContain("secret=");
    }
}
