package com.shm.gateway.filter;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 全链路追踪 WebFilter（Phase 15）
 *
 * <p>在 Gateway 入口将 Micrometer Tracing 的 traceId/spanId 写入
 * {@code MDC}，并注入 {@code X-Trace-Id} 响应头。
 *
 * <h3>执行顺序</h3>
 * <p>Order = -3，在所有业务 Filter 之前执行（{@link SentinelFlowFilter} = -2，
 * {@link JwtAuthGatewayFilter} = -1），确保下游 Filter 的日志已携带 traceId。
 *
 * <h3>WebFlux MDC 注意事项</h3>
 * <p>Micrometer Tracing 1.2.x + Brave 通过 {@code ContextRegistry} 自动
 * 在 Reactor 线程切换时传播 MDC 上下文。本 Filter 只在请求入口/出口做
 * MDC put/remove，依赖于 micrometer-tracing 的自动传播机制来覆盖内部链。
 */
@Component
public class TraceIdFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";

    private final Tracer tracer;

    public TraceIdFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracer = tracerProvider.getIfAvailable();
    }

    @Override
    public int getOrder() {
        return -3;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Tracing 未配置时直接透传（不阻塞请求）
        if (tracer == null) {
            return chain.filter(exchange);
        }

        var currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            var spanContext = currentSpan.context();
            String traceId = spanContext.traceId();
            String spanId = spanContext.spanId();

            MDC.put(TRACE_ID_KEY, traceId);
            MDC.put(SPAN_ID_KEY, spanId);

            // 注入 X-Trace-Id 响应头（CorsConfig 已预设暴露）
            exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);

            log.debug("TraceIdFilter: traceId={}, spanId={}, path={}",
                    traceId, spanId, exchange.getRequest().getURI().getPath());
        }

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    MDC.remove(TRACE_ID_KEY);
                    MDC.remove(SPAN_ID_KEY);
                });
    }
}
