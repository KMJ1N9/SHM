package com.shm.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtAuthGatewayFilter 单元测试
 *
 * <p>验证白名单、Token 提取、JWT 验证、用户上下文注入四个核心行为。
 * 使用测试密钥创建 JwtUtil，不依赖 Spring 容器。
 */
@DisplayName("JwtAuthGatewayFilter — JWT 鉴权过滤器")
class JwtAuthGatewayFilterTest {

    /** 测试用 Access Token 密钥（HMAC-SHA256 需要 ≥ 256 bits，此处用 32 字符 Base64） */
    private static final String TEST_ACCESS_SECRET = "dGVzdC1hY2Nlc3Mtc2VjcmV0LWtleS0zMi1jaGFycw==";
    private static final String TEST_REFRESH_SECRET = "dGVzdC1yZWZyZXNoLXNlY3JldC1rZXktMzItY2hhcnM=";

    private JwtAuthGatewayFilter filter;
    private JwtUtil jwtUtil;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_ACCESS_SECRET, TEST_REFRESH_SECRET);
        objectMapper = new ObjectMapper();
        filter = new JwtAuthGatewayFilter(jwtUtil, objectMapper);
    }

    /**
     * 模拟下游 filter chain — 将 Gateway filter 写入的请求头捕获到 capture Map 中
     *
     * <p>Gateway filter 通过 {@code exchange.mutate().request(mutatedRequest).build()}
     * 创建新 exchange，原始 exchange 看不到 mutated headers。此处从链中 exchange
     * 读取 X-User-* 头并存入 capture Map，供测试断言使用。
     *
     * <p>使用 {@link java.util.HashMap} 而非 ConcurrentHashMap，
     * 因为请求头值可能为 null（ConcurrentHashMap 不允许 null value）。
     */
    private static WebFilterChain passThroughChain(Map<String, Object> capture) {
        return exchange -> {
            capture.put("filter.passed", true);
            capture.put("x-user-id",
                    exchange.getRequest().getHeaders().getFirst("X-User-Id"));
            capture.put("x-user-role",
                    exchange.getRequest().getHeaders().getFirst("X-User-Role"));
            capture.put("x-token-version",
                    exchange.getRequest().getHeaders().getFirst("X-Token-Version"));
            return Mono.empty();
        };
    }

    // ============================================================
    // 白名单测试
    // ============================================================

    @Nested
    @DisplayName("白名单放行")
    class Whitelist {

        @Test
        @DisplayName("POST /api/auth/login — 无需 Token")
        void shouldPassLoginWithoutToken() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.POST, "/api/auth/login").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            assertEquals(Boolean.TRUE, captured.get("filter.passed"));
        }

        @Test
        @DisplayName("POST /api/auth/refresh — 无需 Token")
        void shouldPassRefreshWithoutToken() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.POST, "/api/auth/refresh").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            assertEquals(Boolean.TRUE, captured.get("filter.passed"));
        }

        @Test
        @DisplayName("GET /api/health — 无需 Token")
        void shouldPassHealthWithoutToken() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, "/api/health").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            assertEquals(Boolean.TRUE, captured.get("filter.passed"));
        }

        @Test
        @DisplayName("GET /api/auth/me — 不在白名单，应拒绝")
        void shouldNotPassAuthMeWithoutToken() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, "/api/auth/me").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            assertNull(captured.get("filter.passed"));
            assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        }
    }

    // ============================================================
    // Token 缺失 / 格式错误
    // ============================================================

    @Nested
    @DisplayName("Token 缺失或格式错误 → 1001")
    class MissingOrMalformedToken {

        @Test
        @DisplayName("无 Authorization header → 1001")
        void shouldReturn1001WhenMissingHeader() throws Exception {
            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, "/api/products").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            MockServerHttpResponse response = exchange.getResponse();
            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<?, ?> body = objectMapper.readValue(getResponseBody(response), Map.class);
            assertEquals(1001, body.get("code"));
            assertEquals("请先登录", body.get("message"));
        }

        @Test
        @DisplayName("非 Bearer 格式 → 1001")
        void shouldReturn1001WhenNotBearer() throws Exception {
            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, "/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            Map<?, ?> body = objectMapper.readValue(getResponseBody(exchange.getResponse()), Map.class);
            assertEquals(1001, body.get("code"));
        }

        @Test
        @DisplayName("空 Bearer → 1001")
        void shouldReturn1001WhenEmptyBearer() throws Exception {
            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, "/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            Map<?, ?> body = objectMapper.readValue(getResponseBody(exchange.getResponse()), Map.class);
            assertEquals(1002, body.get("code")); // JJWT 解析空字符串抛出异常 → 1002
        }
    }

    // ============================================================
    // Token 无效 / 过期
    // ============================================================

    @Nested
    @DisplayName("Token 无效或过期 → 1002")
    class InvalidOrExpiredToken {

        @Test
        @DisplayName("伪造 Token → 1002")
        void shouldReturn1002WhenTokenInvalid() throws Exception {
            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, "/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            Map<?, ?> body = objectMapper.readValue(getResponseBody(exchange.getResponse()), Map.class);
            assertEquals(1002, body.get("code"));
        }

        @Test
        @DisplayName("篡改的 Token → 1002")
        void shouldReturn1002WhenTokenTampered() throws Exception {
            String validToken = jwtUtil.generateAccessToken(1L, "user", 1);
            // 篡改最后一个字符
            String tampered = validToken.substring(0, validToken.length() - 1)
                    + (validToken.charAt(validToken.length() - 1) == 'A' ? 'B' : 'A');

            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, "/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            Map<?, ?> body = objectMapper.readValue(getResponseBody(exchange.getResponse()), Map.class);
            assertEquals(1002, body.get("code"));
        }
    }

    // ============================================================
    // 有效 Token — 注入用户上下文
    // ============================================================

    @Nested
    @DisplayName("有效 Token — 注入 X-User-Id / X-User-Role")
    class ValidToken {

        @Test
        @DisplayName("有效 Token → 添加 X-User-Id 和 X-User-Role 请求头")
        void shouldInjectUserHeaders() {
            String token = jwtUtil.generateAccessToken(42L, "user", 5);

            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, "/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            assertEquals(Boolean.TRUE, captured.get("filter.passed"));
            assertEquals("42", captured.get("x-user-id"));
            assertEquals("user", captured.get("x-user-role"));
        }

        @Test
        @DisplayName("admin 角色 Token → 正确传递角色")
        void shouldInjectAdminRole() {
            String token = jwtUtil.generateAccessToken(1L, "admin", 1);

            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, "/api/admin/dashboard")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            assertEquals(Boolean.TRUE, captured.get("filter.passed"));
            assertEquals("1", captured.get("x-user-id"));
            assertEquals("admin", captured.get("x-user-role"));
        }

        @Test
        @DisplayName("cs 角色 Token（客服）→ 正确传递")
        void shouldInjectCsRole() {
            String token = jwtUtil.generateAccessToken(99L, "cs", 3);

            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, "/api/admin/reports")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            assertEquals(Boolean.TRUE, captured.get("filter.passed"));
            assertEquals("99", captured.get("x-user-id"));
            assertEquals("cs", captured.get("x-user-role"));
        }

        @Test
        @DisplayName("有效 Token → 也注入 X-Token-Version")
        void shouldInjectTokenVersion() {
            String token = jwtUtil.generateAccessToken(7L, "user", 42);

            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, "/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Map<String, Object> captured = new HashMap<>();

            filter.filter(exchange, passThroughChain(captured)).block();

            assertEquals("42", captured.get("x-token-version"));
        }
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /**
     * 从 MockServerHttpResponse 中读取响应体为 byte[]
     *
     * <p>使用 {@code getBody()} 而非 {@code getBodyAsString()}，避免
     * MockServerHttpResponse 在 {@code writeWith} 后未正确设置 complete 标志
     * 导致的 IllegalStateException。
     */
    private static byte[] getResponseBodyBytes(MockServerHttpResponse response) {
        try {
            byte[] bytes = response.getBody()
                    .map(buffer -> {
                        byte[] data = new byte[buffer.readableByteCount()];
                        buffer.read(data);
                        return data;
                    })
                    .collectList()
                    .block()
                    .stream()
                    .reduce(new byte[0], (a, b) -> {
                        byte[] merged = new byte[a.length + b.length];
                        System.arraycopy(a, 0, merged, 0, a.length);
                        System.arraycopy(b, 0, merged, a.length, b.length);
                        return merged;
                    });
            return bytes;
        } catch (Exception e) {
            // 兜底：尝试 getBodyAsString（可能被 writeWith 正确完成）
            try {
                String body = response.getBodyAsString().block();
                return body != null ? body.getBytes() : new byte[0];
            } catch (Exception e2) {
                return new byte[0];
            }
        }
    }

    /** 从 MockServerHttpResponse 中读取响应体为 String */
    private static String getResponseBody(MockServerHttpResponse response) {
        return new String(getResponseBodyBytes(response));
    }
}
