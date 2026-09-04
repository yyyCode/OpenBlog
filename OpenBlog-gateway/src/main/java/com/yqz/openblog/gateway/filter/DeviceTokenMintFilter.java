package com.yqz.openblog.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.gateway.handler.GatewayResponses;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 设备令牌签发端点（order=0，晚于 TraceId -3 / JwtCheck -2 / RateLimit -1）。
 * <p>
 * 命中 {@code POST /api/v1/devices/token} → 本地签发 {@code {token, expiresAt}} 并**短接**（不转发
 * business）；其余路径透传。该路径已加入 JwtCheck 白名单、配 RateLimit 的 **IP scope** 限流
 * （60s/10，防按 IP 快速刷"新身份"）；注意此规则 scope 为 IP，第 1 层指纹守卫（仅 FP_IP 触发）
 * 在签发路径不生效。
 * <p>
 * 响应走 ApiResponse.ok + traceId，与 business 契约一致，前端 http.js 无需改动解析。
 */
@Component
public class DeviceTokenMintFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String MINT_PATH = "/api/v1/devices/token";

    private final DeviceTokenService deviceTokenService;
    private final ObjectMapper objectMapper;

    public DeviceTokenMintFilter(DeviceTokenService deviceTokenService, ObjectMapper objectMapper) {
        this.deviceTokenService = deviceTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.POST
                && PATH_MATCHER.match(MINT_PATH, exchange.getRequest().getPath().value())) {
            DeviceTokenService.IssuedToken issued = deviceTokenService.mint();
            // 令牌类响应禁缓存，防浏览器/中间层缓存旧令牌在后续上下文回放
            exchange.getResponse().getHeaders().add(HttpHeaders.CACHE_CONTROL, "no-store");
            return GatewayResponses.writeDataJson(exchange, objectMapper, HttpStatus.OK,
                    Map.of("token", issued.getToken(), "expiresAt", issued.getExpiresAtMs()));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
