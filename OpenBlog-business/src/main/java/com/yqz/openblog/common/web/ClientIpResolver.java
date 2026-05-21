package com.yqz.openblog.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从请求中解析客户端 IP（优先 X-Forwarded-For 首段，否则 remoteAddr）。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip == null ? "" : ip.trim();
    }
}
