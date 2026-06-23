package com.shm.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.util.JwtPayload;
import com.shm.common.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * JWT 鉴权网关过滤器 — 在 Gateway 层统一验证 Access Token
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>白名单路径放行（/api/auth/login, /api/auth/refresh, /api/health）</li>
 *   <li>从 Authorization header 提取 Bearer Token</li>
 *   <li>验证 JWT 签名与过期时间</li>
 *   <li>将用户信息写入请求头（X-User-Id, X-User-Role）传递给下游</li>
 * </ol>
 *
 * <h3>与 Node.js middleware/auth.js 的差异</h3>
 * <p>Gateway 不访问数据库，无法校验 token_version 和封禁状态。
 * 这些检查由下游服务（core-service / admin-service）在下一次 DB 查询时完成，
 * 对应错误码 1003（token_version 不匹配）和 1004（账号已封禁）。
 *
 * <h3>白名单（与 Node.js 完全一致）</h3>
 * <pre>
 *   POST /api/auth/login    — 登录
 *   POST /api/auth/refresh  — 刷新 Token
 *   GET  /api/health         — 健康检查
 * </pre>
 *
 * @see <a href="docs/API接口文档.md">API 接口文档 §4 错误码</a>
 */
@Component
public class JwtAuthGatewayFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGatewayFilter.class);

    /** 与 Node.js middleware/auth.js EXEMPT_PATHS 完全一致的豁免路径 */
    private static final List<WhitelistEntry> WHITELIST = List.of(
            new WhitelistEntry(HttpMethod.POST, "/api/auth/login"),
            new WhitelistEntry(HttpMethod.POST, "/api/auth/refresh"),
            new WhitelistEntry(HttpMethod.GET, "/api/health")
    );

    /** 前缀白名单 — 这些路径下的所有请求都免鉴权（静态资源 + 内部 API + Swagger） */
    private static final List<String> PREFIX_WHITELIST = List.of(
            "/images/",
            "/internal/",   // 内部微服务 API（使用 X-Internal-Token，非 JWT）
            "/swagger-ui.html",
            "/swagger-ui/",
            "/v3/api-docs/",
            "/webjars/"     // Swagger UI 静态资源 (CSS/JS)
    );

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public JwtAuthGatewayFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    /** 最高优先级，在其他 Filter 之前执行 */
    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // ── 1. 白名单放行 ──
        if (isWhitelisted(request)) {
            log.debug("白名单放行: {} {}", request.getMethod(), request.getURI().getPath());
            return chain.filter(exchange);
        }

        // ── 2. 提取 Token ──
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("缺少 Authorization header: {} {}", request.getMethod(), request.getURI().getPath());
            return writeError(exchange, 1001, "请先登录");
        }
        String token = authHeader.substring(7);

        // ── 3. 验证 JWT 签名 ──
        JwtPayload payload;
        try {
            payload = jwtUtil.validateAccessToken(token);
        } catch (ExpiredJwtException e) {
            log.debug("Token 已过期: sub={}", e.getClaims() != null ? e.getClaims().getSubject() : "?");
            return writeError(exchange, 1002, "登录已过期，请重新登录");
        } catch (Exception e) {
            log.debug("Token 无效: {}", e.getMessage());
            return writeError(exchange, 1002, "Token 无效");
        }

        if (payload.getSub() == null) {
            return writeError(exchange, 1002, "Token 无效：缺少用户标识");
        }

        // ── 4. 注入用户上下文到请求头（传递给下游服务） ──
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", String.valueOf(payload.getSub()))
                .header("X-User-Role", payload.getRole() != null ? payload.getRole() : "")
                .header("X-Token-Version", payload.getTv() != null ? String.valueOf(payload.getTv()) : "0")
                .build();

        log.debug("JWT 验证通过: userId={}, role={}", payload.getSub(), payload.getRole());
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /** 检查请求是否在白名单中（精确匹配 + 前缀匹配） */
    private boolean isWhitelisted(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        // 精确匹配（方法 + 路径）
        if (WHITELIST.stream().anyMatch(e -> e.matches(method, path))) {
            return true;
        }
        // 前缀匹配（静态资源等，不限制 HTTP 方法）
        return PREFIX_WHITELIST.stream().anyMatch(path::startsWith);
    }

    /**
     * 写出统一的 JSON 错误响应（与 Node.js 响应格式一致）
     *
     * <p>HTTP 状态码统一返回 200（微信小程序 wx.request 不依赖 HTTP 状态码，
     * 前端通过响应体中的 code 字段判断业务结果）。
     */
    private Mono<Void> writeError(ServerWebExchange exchange, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            Map<String, Object> body = Map.of("code", code, "message", message);
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("序列化错误响应失败: {}", e.getMessage(), e);
            // 兜底：返回简单 JSON 字符串
            String fallback = "{\"code\":" + code + ",\"message\":\"" + message + "\"}";
            byte[] bytes = fallback.getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        }
    }

    // ============================================================
    // 内部记录类
    // ============================================================

    /** 白名单条目：方法 + 路径精确匹配 */
    private record WhitelistEntry(HttpMethod method, String path) {
        boolean matches(HttpMethod m, String p) {
            return this.method == m && this.path.equals(p);
        }
    }
}
