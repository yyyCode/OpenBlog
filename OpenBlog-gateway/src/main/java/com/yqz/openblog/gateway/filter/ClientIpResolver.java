package com.yqz.openblog.gateway.filter;

import org.springframework.web.server.ServerWebExchange;

/**
 * 客户端真实 IP 解析（网关各过滤器共用）。
 * <p>
 * 抽成独立工具类而非各自实现：IP 是限流分桶与访问审计的共同口径，两份实现一旦漂移，
 * 会出现「限流按 A 计、日志按 B 记」的排查黑洞。
 * <p>
 * 优先级：X-Real-IP → X-Forwarded-For 首跳 → TCP 对端地址 → "unknown"。
 * nginx 用 {@code $remote_addr} 覆写 X-Real-IP（不可伪造）故优先；
 * X-Forwarded-For 首跳由客户端自报、可伪造，仅作回退。
 */
final class ClientIpResolver {

    private ClientIpResolver() {
    }

    static String resolve(ServerWebExchange exchange) {
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
}
