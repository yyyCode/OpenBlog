package com.yqz.openblog.gateway.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yqz.openblog.gateway.config.GatewayProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AccessLogFilterTest {

    private final GatewayProperties props = new GatewayProperties();
    private final AccessLogFilter filter = new AccessLogFilter(props);

    private Logger filterLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        filterLogger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(appender);
    }

    @Test
    void logsIpMethodPathStatusAndTraceId() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/articles?keyword=spring")
                        .header("X-Real-IP", "203.0.113.7")
                        .build());
        // 单测直接调本过滤器，链上没有 TraceIdFilter：手动写入它本该留下的属性。
        // 生产环境两者都是 GlobalFilter，TraceIdFilter(-3) 先于本过滤器(-4) 收尾，属性必然已就位。
        exchange.getAttributes().put(TraceIdFilter.TRACE_ID_ATTR, "t-abc");
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        filter.filter(exchange, ex -> Mono.empty()).block();

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("ip=203.0.113.7")
                .contains("method=GET")
                .contains("path=/api/v1/articles")
                .contains("status=200")
                .contains("traceId=t-abc")
                .contains("cost=");
        // query string 刻意不入日志（防一次性令牌/邮箱等敏感参数落盘）
        assertThat(event.getFormattedMessage()).doesNotContain("keyword=spring");
    }

    @Test
    void prefersRealIpOverForwardedFor() {
        // 与限流共用 ClientIpResolver：nginx 覆写的 X-Real-IP 优先于客户端可伪造的 XFF
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/x")
                        .header("X-Forwarded-For", "1.2.3.4")
                        .header("X-Real-IP", "9.9.9.9")
                        .build());

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(onlyEvent().getFormattedMessage()).contains("ip=9.9.9.9");
    }

    @Test
    void logsFailureAsWarningWhenChainErrors() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/x").header("X-Real-IP", "1.2.3.4").build());

        filter.filter(exchange, ex -> Mono.error(new IllegalStateException("boom")))
                .onErrorResume(e -> Mono.empty())
                .block();

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("gateway access failed")
                .contains("ip=1.2.3.4")
                .contains("boom");
    }

    @Test
    void logsCancelledRequestAtInfo() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/x").header("X-Real-IP", "1.2.3.4").build());

        // 模拟客户端提前断开：订阅后立即 dispose，链路以 ON_CANCEL 终止
        Disposable subscription = filter.filter(exchange, ex -> Mono.never()).subscribe();
        subscription.dispose();

        ILoggingEvent event = onlyEvent();
        // 取消属日常流量（前端切路由/关页面），记 INFO 以免刷屏淹没真正的失败
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("gateway access cancelled")
                .contains("ip=1.2.3.4");
    }

    @Test
    void stillLogsWhenForwardedForIsCommaOnly() {
        // 回归防线：",".split(",") 得到长度 0 的数组，老写法取 [0] 会抛 AIOOBE；
        // 该异常在 doFinally 里被 Reactor 吞掉，请求方用畸形头就让自己的访问日志整条消失。
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/x").header("X-Forwarded-For", ",").build());

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(onlyEvent().getFormattedMessage()).contains("ip=");
    }

    @Test
    void trimsWhitespaceSoRateLimitBucketsCannotBeRotated() {
        // 带空格的头若原样入 key，"1.2.3.4 " 与 "1.2.3.4" 会落进不同限流桶
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/x").header("X-Real-IP", " 1.2.3.4 ").build());

        filter.filter(exchange, ex -> Mono.empty()).block();

        // 断言到下一个字段的边界，才能证明首尾空格真的被去掉了
        assertThat(onlyEvent().getFormattedMessage()).contains("ip=1.2.3.4 method=");
    }

    @Test
    void forwardsWithoutLoggingWhenDisabled() {
        props.getAccessLog().setEnabled(false);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/x").build());
        AtomicBoolean forwarded = new AtomicBoolean(false);

        filter.filter(exchange, ex -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(appender.list).isEmpty();
    }

    @Test
    void runsOutsideTraceIdFilterSoTraceIdIsAvailable() {
        // 顺序是契约的一部分：必须在 TraceIdFilter 外层，否则记录时 traceId 还没写入 exchange
        assertThat(filter.getOrder()).isLessThan(new TraceIdFilter().getOrder());
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }
}
