package com.shm.gateway.config;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Sentinel 限流 WebFilter（Phase 9）
 *
 * <p>替代 Sentinel 原生的 SentinelGatewayFilter，因为 Sentinel 1.8.6
 * 与 Spring Cloud Gateway 4.1.6 存在路由识别兼容性问题——
 * 原生过滤器只在部分路由（health/core-auth）上生效。
 *
 * <h3>限流策略（与 Node.js 令牌桶一致）</h3>
 * <ul>
 *   <li>敏感 API (QPS=10): /api/admin, /api/orders, /api/reports</li>
 *   <li>普通 API (QPS=60): 所有 /api/** 请求</li>
 *   <li>白名单（不限流）: /api/health, /api/auth/login, /api/auth/refresh</li>
 * </ul>
 *
 * <p>流控规则由 Nacos 加载（dataId: sentinel-flow-rules），
 * 此过滤器负责调用 SphU.entry() 触发规则检查。
 */
@Component
public class SentinelFlowFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SentinelFlowFilter.class);

    /** 敏感 API 资源名 — Nacos 中配置 QPS=10（引用共享常量） */
    static final String RESOURCE_SENSITIVE = SentinelConstants.RESOURCE_SENSITIVE;

    /** 普通 API 资源名 — Nacos 中配置 QPS=60（引用共享常量） */
    static final String RESOURCE_NORMAL = SentinelConstants.RESOURCE_NORMAL;

    /** 白名单路径前缀 — 不限流 */
    private static final Set<String> WHITELIST_PREFIXES = Set.of(
            "/api/health",
            "/api/auth/login",
            "/api/auth/refresh"
    );

    /** 敏感路径前缀 */
    private static final String[] SENSITIVE_PREFIXES = {
            "/api/admin",
            "/api/orders",
            "/api/reports"
    };

    /**
     * 将请求分类到 Sentinel 资源名
     */
    private String resolveResource(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        if (path == null || path.isEmpty()) {
            return null; // 不限流
        }

        // 白名单检查
        for (String white : WHITELIST_PREFIXES) {
            if (path.startsWith(white)) {
                return null; // 不限流
            }
        }

        // 敏感 API 检查
        for (String sensitive : SENSITIVE_PREFIXES) {
            if (path.startsWith(sensitive)) {
                return RESOURCE_SENSITIVE;
            }
        }

        // 所有其他 /api/** 归类为普通 API
        if (path.startsWith("/api/")) {
            return RESOURCE_NORMAL;
        }

        // 非 API 路径（如 /internal/）不限流
        return null;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String resource = resolveResource(exchange);
        if (resource == null) {
            return chain.filter(exchange);
        }

        try {
            Entry entry = SphU.entry(resource, EntryType.IN);
            return chain.filter(exchange)
                    .doFinally(signal -> entry.exit());
        } catch (BlockException e) {
            log.debug("[Sentinel] 限流触发: resource={}, path={}",
                    resource, exchange.getRequest().getURI().getPath());
            return blockResponse(exchange);
        }
    }

    private Mono<Void> blockResponse(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(SentinelConstants.BLOCK_RESPONSE.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
