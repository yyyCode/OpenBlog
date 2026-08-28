package com.yqz.openblog.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "openblog.jwt.secret=openblog-gateway-test-secret-key-0123456789abcdef")
class GatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoadsAndRoutesConfigured() {
        int routes = routeLocator.getRoutes().collectList().block().size();
        assertThat(routes).isGreaterThan(0);
    }
}
