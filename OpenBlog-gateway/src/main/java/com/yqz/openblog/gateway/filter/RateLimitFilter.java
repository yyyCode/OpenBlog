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
 * <p>
 * key 规则：
 * <ul>
 *   <li>scope=IP → {@code gateway:rl:{IP}_{path}}</li>
 *   <li>scope=IP_UID → 已校验业务 JWT 的 uid 存在时 {@code gateway:rl:{IP}_{uid}_{path}}，否则纯 IP</li>
 *   <li>scope=FP_IP → **有合法设备令牌**（X-Device-Token，网关私钥签发）时取其中随机 deviceId 分桶
 *       {@code gateway:rl:{dev}_{IP}_{path}}；无/失效令牌一律纯 IP。裸指纹 header 不再换桶——这是防构造
 *       第 2 层语义：客户端无法每请求伪造一个新身份，新身份必须逐个经 IP 限流的签发端点获取。</li>
 * </ul>
 * FP_IP 额外经 FingerprintRotationGuard 预检（第 1 层）：同一 IP 在窗口内换新指纹超过预算 → 疑似轮换，
 * 直接 4290 不计数。指纹降级为附带的风险信号（日志 / 层 3 账号×设备绑定），不再作为安全判定主键。
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    /** 设备令牌头。防御上限：超长 token 不进入 jjwt 解析（防恶意大 header 拖慢限流前置）。 */
    private static final int MAX_DEVICE_TOKEN_LENGTH = 512;

    private final GatewayProperties props;
    private final SlidingWindowLimiter limiter;
    private final JwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;
    private final FingerprintRotationGuard rotationGuard;
    private final DeviceTokenService deviceTokenService;

    public RateLimitFilter(GatewayProperties props, SlidingWindowLimiter limiter,
                           JwtVerifier jwtVerifier, ObjectMapper objectMapper,
                           FingerprintRotationGuard rotationGuard,
                           DeviceTokenService deviceTokenService) {
        this.props = props;
        this.limiter = limiter;
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
        this.rotationGuard = rotationGuard;
        this.deviceTokenService = deviceTokenService;
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
            // 第 1 层防构造预检：指纹是自报 header，脚本可每请求伪造新指纹。同一 IP 窗口内新指纹超预算
            // → 判定轮换，直接拒绝（不计数、guard 内不落库）。仅 FP_IP 规则需要携带指纹信号。
            String fp = rule.getScope() == GatewayProperties.Scope.FP_IP
                    ? deviceFingerprint(exchange) : null;
            if (fp != null && rotationGuard.isOverBudget(ip, fp,
                    props.getRateLimit().getFpWindowMs(),
                    props.getRateLimit().getFpMaxDistinctPerIp())) {
                log.warn("gateway fingerprint rotation suspected, rate limited: ip={}", ip);
                return false;
            }
            String key = buildKey(exchange, rule, ip);
            try {
                return limiter.tryAcquire(key, rule.getWindowMs(), rule.getLimit(),
                        System.currentTimeMillis(), UUID.randomUUID().toString());
            } catch (RuntimeException e) {
                // Redis 不可用时放行（fail open），避免把每个限流路径都打成 500
                log.warn("rate limit backend unavailable, allowing request: key={} err={}", key, e.toString());
                return true;
            }
        }).subscribeOn(Schedulers.boundedElastic()).flatMap(allowed -> {
            if (allowed) {
                return chain.filter(exchange);
            }
            log.warn("gateway rate limited: path={}", exchange.getRequest().getPath());
            return GatewayResponses.writeJson(exchange, objectMapper,
                    HttpStatus.TOO_MANY_REQUESTS, 4290, "请求过于频繁，请稍后再试");
        });
    }

    /** 按 scope 构建限流 key（见类注释）；ip 由调用方一次解析传入。 */
    private String buildKey(ServerWebExchange exchange, GatewayProperties.Rule rule, String ip) {
        if (rule.getScope() == GatewayProperties.Scope.IP_UID) {
            String uid = resolveUid(exchange);
            return "gateway:rl:" + ip + (uid != null ? "_" + uid : "") + "_" + rule.getPath();
        }
        if (rule.getScope() == GatewayProperties.Scope.FP_IP) {
            // 第 2 层语义：只有网关签发并验签通过的设备令牌才认可"设备身份"；无/失效令牌忽略指纹、纯 IP 分桶。
            String dev = resolveDeviceId(exchange);
            return "gateway:rl:" + (dev != null ? dev + "_" : "") + ip + "_" + rule.getPath();
        }
        return "gateway:rl:" + ip + "_" + rule.getPath();
    }

    /** 提取并校验设备指纹头；缺失或非法（空/超长/含非法字符）一律视为无指纹。 */
    private String deviceFingerprint(ServerWebExchange exchange) {
        String raw = exchange.getRequest().getHeaders().getFirst("X-Device-Fingerprint");
        if (raw == null) {
            return null;
        }
        String fp = raw.trim();
        // 32 位 hex 是常态；限 16~64 位字母数字-，防 header 注入与超长 Redis key
        return fp.matches("^[A-Za-z0-9-]{16,64}$") ? fp : null;
    }

    /** @return 合法设备令牌中的随机 deviceId；无令牌/超长/验签失败/过期一律 null（降级纯 IP）。 */
    private String resolveDeviceId(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst("X-Device-Token");
        if (token == null || token.isBlank() || token.length() > MAX_DEVICE_TOKEN_LENGTH) {
            return null;
        }
        return deviceTokenService.deviceIdOf(token.trim());
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
        // nginx 用 $remote_addr 覆写 X-Real-IP（不可伪造），优先使用；X-Forwarded-For 首跳是客户端可控的，仅作回退。
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
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
