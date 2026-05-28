package com.yqz.openblog.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从请求中解析客户端 IP（优先 X-Forwarded-For 第一个、其次 X-Real-IP、最后 remoteAddr）。
 * <p>
 * 若服务部署在可信反向代理后，请由代理写入真实 IP；直连时请勿信任客户端伪造的 X-Forwarded-For。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            String first = comma >= 0 ? xff.substring(0, comma).trim() : xff.trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    /**
     * Redis key 片段安全化（IPv6 含冒号等）。
     */
    public static String toRedisKeySegment(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        return ip.replace(':', '_').replace('%', '_');
    }
}
