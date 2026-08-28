package com.yqz.openblog.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.gateway.config.GatewayProperties;
import com.yqz.openblog.gateway.handler.GatewayResponses;
import com.yqz.openblog.redis.limiter.SlidingWindowLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * 限流防刷（order=-1）：复用 framework-redis SlidingWindowLimiter（Redis ZSET+Lua 原子滑动窗口）。
 * 阻塞 Redis 调用在 boundedElastic 执行，chain.filter 回到事件循环，不阻塞 Netty。
 * key = gateway:rl:{IP}[_{uid}]_{path}；scope=IP_UID 时从已校验 token 解析 uid（无 token 退化为纯 IP）。
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final GatewayProperties props;
    private final SlidingWindowLimiter limiter;
    private final JwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(GatewayProperties props, SlidingWindowLimiter limiter,
                           JwtVerifier jwtVerifier, ObjectMapper objectMapper) {
        this.props = props;
        this.limiter = limiter;
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!props.getRateLimit().isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        return props.getRateLimit().getRules().stream()
                .filter(r -> r.getPath() != null && PATH_MATCHER.match(r.getPath(), path))
                .findFirst()
                .map(rule -> applyRule(exchange, chain, rule))
                .orElseGet(() -> chain.filter(exchange));
    }

    private Mono<Void> applyRule(ServerWebExchange exchange, GatewayFilterChain chain,
                                 GatewayProperties.Rule rule) {
        return Mono.fromCallable(() -> {
            String ip = clientIp(exchange);
            String uid = null;
            if (rule.getScope() == GatewayProperties.Scope.IP_UID) {
                uid = resolveUid(exchange);
            }
            String key = "gateway:rl:" + ip + (uid != null ? "_" + uid : "") + "_" + rule.getPath();
            return limiter.tryAcquire(key, rule.getWindowMs(), rule.getLimit(),
                    System.currentTimeMillis(), UUID.randomUUID().toString());
        }).subscribeOn(Schedulers.boundedElastic()).flatMap(allowed -> {
            if (allowed) {
                return chain.filter(exchange);
            }
            log.warn("gateway rate limited: path={}", exchange.getRequest().getPath());
            return GatewayResponses.writeJson(exchange, objectMapper,
                    HttpStatus.TOO_MANY_REQUESTS, 4290, "请求过于频繁，请稍后再试");
        });
    }

    private String resolveUid(ServerWebExchange exchange) {
        String token = bearerToken(exchange);
        Long uid = token != null ? jwtVerifier.parseUserId(token) : null;
        return uid != null ? uid.toString() : null;
    }

    private String bearerToken(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    private String clientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
