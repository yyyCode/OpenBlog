package com.yqz.openblog.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.gateway.config.GatewayProperties;
import com.yqz.openblog.gateway.handler.GatewayResponses;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 粗校验（order=-2）。
 * 语义：白名单外路径「无 token → 放行交由 business 判定（避免误伤匿名公共路由）；
 * 带 token 但无效/过期 → 边缘 401」。token 原样透传，business 保留完整授权。
 */
@Component
public class JwtCheckFilter implements GlobalFilter, Ordered {

    private final GatewayProperties props;
    private final JwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtCheckFilter(GatewayProperties props, JwtVerifier jwtVerifier, ObjectMapper objectMapper) {
        this.props = props;
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isSkipped(path)) {
            return chain.filter(exchange);
        }
        String token = bearerToken(exchange);
        if (token != null && jwtVerifier.parseUserId(token) == null) {
            return GatewayResponses.writeJson(exchange, objectMapper,
                    HttpStatus.UNAUTHORIZED, 4010, "登录已失效");
        }
        return chain.filter(exchange);
    }

    private boolean isSkipped(String path) {
        return props.getAuth().getSkipPaths().stream()
                .anyMatch(p -> pathMatcher.match(p, path));
    }

    private String bearerToken(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
