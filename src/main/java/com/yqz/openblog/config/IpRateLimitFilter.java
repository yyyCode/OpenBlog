package com.yqz.openblog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.article.limiter.SlidingWindowLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 全站按 IP 滑动窗口限流；不统计 OPTIONS（避免 CORS 预检被误伤）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class IpRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IpRateLimitFilter.class);
    private static final int RATE_LIMIT_CODE = 4291;

    private final RateLimitProperties properties;
    private final SlidingWindowLimiter limiter;
    private final ObjectMapper objectMapper;

    public IpRateLimitFilter(
            RateLimitProperties properties,
            SlidingWindowLimiter limiter,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String ip = ClientIpResolver.resolve(request);
        String redisKey = "openblog:rl:global:ip:" + ClientIpResolver.toRedisKeySegment(ip);
        long nowMs = System.currentTimeMillis();
        String member = nowMs + "-" + UUID.randomUUID();

        boolean allowed;
        try {
            allowed = limiter.tryAcquire(
                    redisKey,
                    properties.getWindowMs(),
                    properties.getMaxRequests(),
                    nowMs,
                    member
            );
        } catch (Exception e) {
            log.warn("rate limit skipped (fail-open), ip={}", ip, e);
            filterChain.doFilter(request, response);
            return;
        }

        if (!allowed) {
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        long retryAfterSec = Math.max(1L, (properties.getWindowMs() + 999) / 1000);
        response.setHeader("Retry-After", String.valueOf(retryAfterSec));

        ApiResponse<Object> body = ApiResponse.fail(RATE_LIMIT_CODE, "请求过于频繁，请稍后再试");
        byte[] json = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        response.setContentLength(json.length);
        response.getOutputStream().write(json);
    }
}
