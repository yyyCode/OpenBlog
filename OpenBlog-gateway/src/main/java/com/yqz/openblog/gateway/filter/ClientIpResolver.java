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
        // 一律 trim：该返回值既是审计口径也是限流分桶 key，带空格的头会让 "1.2.3.4" 与 "1.2.3.4 "
        // 落进不同桶，等于每请求换个后缀就能绕过 IP 限流。
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // 用 indexOf/substring 而非 split：",".split(",") 返回长度 0 的数组（Java 丢弃尾随空串），
            // 取 [0] 抛 AIOOBE。该异常在 AccessLogFilter 的 doFinally 回调里会被 Reactor 吞掉，
            // 于是请求方只要发一个畸形头就能让自己的访问日志整条消失——审计日志不能有这种后门。
            int comma = xff.indexOf(',');
            String first = (comma >= 0 ? xff.substring(0, comma) : xff).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
