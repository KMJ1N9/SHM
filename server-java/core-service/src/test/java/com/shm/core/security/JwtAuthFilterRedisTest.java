package com.shm.core.security;

import com.shm.common.constant.RedisKeys;
import com.shm.common.model.entity.User;
import com.shm.common.util.JwtPayload;
import com.shm.common.util.JwtUtil;
import com.shm.core.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JwtAuthFilter Token 黑名单 Redis 单元测试（Phase 10 P1-2）
 *
 * <p>覆盖黑名单三种路径：
 * <ol>
 *   <li>用户在黑名单中 → 返回 401（强制下线）</li>
 *   <li>用户不在黑名单中 → 正常通过</li>
 *   <li>Redis 不可用 → 降级放行（可用性优先）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterRedisTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserRepository userRepo;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtUtil, userRepo, redis);
    }

    // ============================================================
    // Blacklisted → reject
    // ============================================================

    @Test
    void jwtAuth_blacklistedUser_shouldReturn401() throws Exception {
        // Given: Gateway 路径 — X-User-Id 头存在
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/products");
        when(request.getHeader("X-User-Id")).thenReturn("42");

        // DB 用户正常（未封禁）
        User user = User.builder().id(42L).role("user").status("active")
                .tokenVersion(0).build();
        when(userRepo.findById(42L)).thenReturn(user);

        // 但在黑名单中（管理员已强制下线）
        when(redis.hasKey(RedisKeys.tokenBlacklistKey(42L))).thenReturn(true);

        // 模拟 response writer
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        // When
        filter.doFilterInternal(request, response, chain);

        // Then: chain.doFilter 未调用（被拦截）
        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(401);
        assertTrue(writer.toString().contains("Token 已失效"));
    }

    // ============================================================
    // Not blacklisted → allow
    // ============================================================

    @Test
    void jwtAuth_notBlacklisted_shouldAllow() throws Exception {
        // Given: Gateway 路径
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/products");
        when(request.getHeader("X-User-Id")).thenReturn("1");

        User user = User.builder().id(1L).role("user").status("active")
                .tokenVersion(0).build();
        when(userRepo.findById(1L)).thenReturn(user);

        // 不在黑名单中
        when(redis.hasKey(RedisKeys.tokenBlacklistKey(1L))).thenReturn(false);

        // When
        filter.doFilterInternal(request, response, chain);

        // Then: 请求通过
        verify(chain).doFilter(request, response);
    }

    // ============================================================
    // Redis 不可用 → 降级放行
    // ============================================================

    @Test
    void jwtAuth_redisUnavailable_shouldDegradeAndAllow() throws Exception {
        // Given: Gateway 路径
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/products");
        when(request.getHeader("X-User-Id")).thenReturn("1");

        User user = User.builder().id(1L).role("user").status("active")
                .tokenVersion(0).build();
        when(userRepo.findById(1L)).thenReturn(user);

        // Redis 挂掉
        when(redis.hasKey(anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        // When
        filter.doFilterInternal(request, response, chain);

        // Then: 降级放行（可用性 > 一致性）
        verify(chain).doFilter(request, response);
    }

    // ============================================================
    // JWT 直接路径 — 也检查黑名单
    // ============================================================

    @Test
    void jwtAuth_directJwtPath_blacklisted_shouldReturn401() throws Exception {
        // Given: 无 X-User-Id（直接 JWT 路径）
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/products");
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");

        // JWT 验证通过
        JwtPayload payload = new JwtPayload(5L, "user", 0, null);
        when(jwtUtil.validateAccessToken("valid-token")).thenReturn(payload);

        User user = User.builder().id(5L).role("user").status("active")
                .tokenVersion(0).build();
        when(userRepo.findById(5L)).thenReturn(user);

        // 在黑名单中
        when(redis.hasKey(RedisKeys.tokenBlacklistKey(5L))).thenReturn(true);

        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        // When
        filter.doFilterInternal(request, response, chain);

        // Then: 被拦截
        verify(chain, never()).doFilter(request, response);
        assertTrue(writer.toString().contains("Token 已失效"));
    }

    // ============================================================
    // 白名单路径 — 不触发黑名单检查
    // ============================================================

    @Test
    void jwtAuth_whitelistPath_shouldSkipBlacklistCheck() throws Exception {
        // Given: 登录接口在白名单中
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/auth/login");

        // When
        filter.doFilterInternal(request, response, chain);

        // Then: 直接放行，不调用 Redis 黑名单检查
        verify(chain).doFilter(request, response);
        verify(redis, never()).hasKey(anyString());
    }
}
