package com.yqz.openblog.gateway.filter;

import com.yqz.openblog.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 访问日志（order=-4，最外层）：请求结束后打印来源 IP、方法、路径、状态码、耗时与 traceId。
 * <p>
 * 排在 {@link TraceIdFilter}（-3）之外，故记录发生在整条链（限流 / JWT / 下游调用）全部结束之后，
 * 状态码与耗时都是最终值。traceId 在此刻已写入 exchange 属性，直接取出显式打印——
 * 日志 pattern 里的 {@code %X{traceId}} 依赖 MDC，而 WebFlux 下 MDC 不跨线程，网关这里取不到值。
 * <p>
 * 只记录 path、<b>不记录 query string</b>：部分端点的查询参数可能带一次性令牌/邮箱等敏感值，
 * 一旦落盘难以回收。确需排查时再显式放开，并自行做脱敏。
 * <p>
 * 关闭开关：{@code openblog.gateway.access-log.enabled=false}。
 */
@Component
public class AccessLogFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    private final GatewayProperties props;

    public AccessLogFilter(GatewayProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!props.getAccessLog().isEnabled()) {
            return chain.filter(exchange);
        }
        long startNanos = System.nanoTime();
        // doFinally 只给终止信号、拿不到异常对象，用 holder 记下异常供统一出口打印
        AtomicReference<Throwable> error = new AtomicReference<>();

        return chain.filter(exchange)
                .doOnError(error::set)
                .doFinally(signal -> logAccess(exchange, startNanos, signal, error.get()));
    }

    private void logAccess(ServerWebExchange exchange, long startNanos, SignalType signal, Throwable error) {
        String ip = ClientIpResolver.resolve(exchange);
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getPath().value();
        long costMs = (System.nanoTime() - startNanos) / 1_000_000;
        String traceId = TraceIdFilter.traceIdOf(exchange);

        if (signal == SignalType.ON_ERROR) {
            // 此时响应码可能尚未由 GatewayErrorHandler 写入（异常处理器在过滤器链之外），故只报异常本身
            log.warn("gateway access failed: ip={} method={} path={} cost={}ms traceId={} err={}",
                    ip, method, path, costMs, traceId, error != null ? error.toString() : signal);
            return;
        }
        if (signal == SignalType.CANCEL) {
            // 客户端提前断开：网关超时、用户关页面、上游慢导致连接被弃
            log.warn("gateway access cancelled: ip={} method={} path={} cost={}ms traceId={}",
                    ip, method, path, costMs, traceId);
            return;
        }
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        log.info("gateway access: ip={} method={} path={} status={} cost={}ms traceId={}",
                ip, method, path, status != null ? status.value() : "-", costMs, traceId);
    }

    @Override
    public int getOrder() {
        return -4;
    }
}
