package com.shm.admin.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Feign 内部调用请求拦截器 — 向所有 Feign 请求添加
 * {@code X-Internal-Token} + 全链路追踪头（Phase 15）
 *
 * <p>im-connector 的 {@code InternalAuthInterceptor} 校验 Token 头。
 * 当 {@code internal.token} 为空时（本地开发），不注入 Token 头。
 *
 * <p>追踪头传播：从当前 Span 提取 traceId/spanId 注入 X-Trace-Id / X-Span-Id，
 * 确保跨 Feign 调用的 trace 被 Zipkin 串联。使用 {@link ObjectProvider} 避免
 * Tracing 未正确配置时启动失败。
 */
@Component
public class InternalTokenRequestInterceptor implements RequestInterceptor {

    private final String internalToken;
    private final Tracer tracer;

    public InternalTokenRequestInterceptor(@Value("${internal.token:}") String internalToken,
                                           ObjectProvider<Tracer> tracerProvider) {
        this.internalToken = internalToken;
        this.tracer = tracerProvider.getIfAvailable();
    }

    @Override
    public void apply(RequestTemplate template) {
        if (internalToken != null && !internalToken.isEmpty()) {
            template.header("X-Internal-Token", internalToken);
        }

        // 传播全链路追踪头（Phase 15）
        if (tracer != null) {
            var currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                var ctx = currentSpan.context();
                template.header("X-Trace-Id", ctx.traceId());
                template.header("X-Span-Id", ctx.spanId());
            }
        }
    }
}
