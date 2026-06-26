package com.shm.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;

import jakarta.annotation.PostConstruct;

/**
 * Sentinel Gateway Block 响应自定义（Phase 9 + Phase 15 追踪增强）
 *
 * <p>默认 Sentinel Gateway Block 返回空响应，此处自定义为项目统一 JSON 格式：
 * <pre>{@code {"code": 4006, "message": "请求过于频繁，请稍后再试"}}</pre>
 *
 * <p>错误码 4006 对应 Node.js 的 RATE_LIMITED 错误码。
 */
@Configuration
public class SentinelBlockHandlerConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelBlockHandlerConfig.class);

    private final Tracer tracer;

    public SentinelBlockHandlerConfig(ObjectProvider<Tracer> tracerProvider) {
        this.tracer = tracerProvider.getIfAvailable();
    }

    @PostConstruct
    public void init() {
        GatewayCallbackManager.setBlockHandler(
                (ServerWebExchange exchange, Throwable ex) -> {
                    String traceId = tracer != null && tracer.currentSpan() != null
                            ? tracer.currentSpan().context().traceId() : MDC.get("traceId");
                    log.debug("[Sentinel] 限流触发: {} {} traceId={}",
                            exchange.getRequest().getMethod(),
                            exchange.getRequest().getURI().getPath(),
                            traceId != null ? traceId : "-");
                    return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(SentinelConstants.BLOCK_RESPONSE);
                }
        );
        log.info("[Sentinel Gateway] 自定义 Block 响应已注册: code=4006, status=429");
    }
}
