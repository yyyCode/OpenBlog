package com.yqz.openblog.gateway;

import com.yqz.openblog.gateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "openblog.jwt.secret=openblog-gateway-test-secret-key-0123456789abcdef",
                "openblog.device-token.secret=openblog-device-token-test-secret-key-0123456789"
        })
class GatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private GatewayProperties gatewayProperties;

    @Test
    void contextLoadsAndRoutesConfigured() {
        int routes = routeLocator.getRoutes().collectList().block().size();
        assertThat(routes).isGreaterThan(0);
    }

    @Test
    void gatewayConfigBinds() {
        // 锁定 skip-paths / 限流规则的绑定（评审重要发现：路径拼错会静默失效限流）
        assertThat(gatewayProperties.getAuth().getSkipPaths())
                .contains("/api/v1/auth/email-code");
        assertThat(gatewayProperties.getRateLimit().getRules())
                .anyMatch(r -> "/api/v1/auth/email-code".equals(r.getPath()));
        assertThat(gatewayProperties.getRateLimit().getRules())
                .anyMatch(r -> r.getScope() == GatewayProperties.Scope.IP_UID);
        assertThat(gatewayProperties.getRateLimit().getRules())
                .anyMatch(r -> r.getScope() == GatewayProperties.Scope.FP_IP);
        // 设备令牌签发端点：JwtCheck 白名单 + IP scope 限流（绑定漏配会让新身份可被随意刷取）
        assertThat(gatewayProperties.getAuth().getSkipPaths())
                .contains("/api/v1/devices/token");
        assertThat(gatewayProperties.getRateLimit().getRules())
                .anyMatch(r -> "/api/v1/devices/token".equals(r.getPath())
                        && r.getScope() == GatewayProperties.Scope.IP);
    }
}
