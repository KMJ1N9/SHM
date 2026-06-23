package com.shm.core.security;

import com.shm.common.constant.RedisKeys;
import com.shm.common.exception.ErrorCode;
import com.shm.common.util.JwtPayload;
import com.shm.common.util.JwtUtil;
import com.shm.core.repository.UserRepository;
import com.shm.common.model.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * JWT 鉴权过滤器（与 Node.js middleware/auth.js 行为完全一致）
 *
 * <h3>双路径鉴权（Phase 7 Gateway 适配）</h3>
 * <ol>
 *   <li><b>Gateway 信任路径</b> — 请求头含 {@code X-User-Id} 时，跳过 JWT 加密验证，
 *       信任 Gateway 的签名校验结果，仅做 DB 防御检查（用户存在/封禁/tokenVersion）</li>
 *   <li><b>直接访问路径</b> — 无 {@code X-User-Id} 时，完整 JWT 签名验证 + DB 检查
 *       （本地开发无 Gateway 场景）</li>
 * </ol>
 *
 * <p>白名单放行（/api/auth/login, /api/auth/refresh, /api/health, /error）
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /** 不需要鉴权的路径 */
    private static final List<WhitelistEntry> WHITELIST = List.of(
            new WhitelistEntry("POST", "/api/auth/login"),
            new WhitelistEntry("POST", "/api/auth/refresh"),
            new WhitelistEntry("GET", "/api/health"),
            new WhitelistEntry("GET", "/error"),
            new WhitelistEntry("POST", "/error")
    );

    private final JwtUtil jwtUtil;
    private final UserRepository userRepo;
    private final StringRedisTemplate redis;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepo, StringRedisTemplate redis) {
        this.jwtUtil = jwtUtil;
        this.userRepo = userRepo;
        this.redis = redis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 1. 内部 API 路径放行（由 InternalAuthInterceptor 校验 X-Internal-Token）
        if (request.getRequestURI().startsWith("/internal/")) {
            chain.doFilter(request, response);
            return;
        }

        // 2. 白名单放行
        if (isWhitelisted(request)) {
            chain.doFilter(request, response);
            return;
        }

        // 3. Gateway 透传路径：X-User-Id 头存在 → 信任 Gateway 的 JWT 验证结果
        String gatewayUserId = request.getHeader("X-User-Id");
        if (gatewayUserId != null && !gatewayUserId.isEmpty()) {
            if (!authenticateFromGateway(request, response, gatewayUserId)) {
                return; // 错误响应已在 authenticateFromGateway 中写出
            }
        } else {
            // 4. 直接访问路径：完整 JWT 验证（本地开发 / 无 Gateway 场景）
            if (!authenticateFromJwt(request, response)) {
                return; // 错误响应已在 authenticateFromJwt 中写出
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Gateway 信任路径 — 跳过 JWT 加密验证，仍做 DB 防御检查
     *
     * <p>Gateway 已验证 JWT 签名和过期时间，此处补充 Gateway 无法做的检查：
     * <ul>
     *   <li>用户是否存在</li>
     *   <li>账号是否被封禁（code 1004）</li>
     *   <li>token_version 是否匹配（code 1003，防止管理员封号/改密后旧 Token 仍可用）</li>
     * </ul>
     *
     * @return true 鉴权通过，false 已写出错误响应
     */
    private boolean authenticateFromGateway(HttpServletRequest request,
                                            HttpServletResponse response,
                                            String userIdStr) throws IOException {
        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            log.warn("Gateway X-User-Id 格式无效: {}", userIdStr);
            sendError(response, ErrorCode.TOKEN_EXPIRED, "用户标识无效");
            return false;
        }

        User user = userRepo.findById(userId);
        if (user == null) {
            sendError(response, ErrorCode.TOKEN_EXPIRED, "用户不存在");
            return false;
        }
        if ("banned".equals(user.getStatus())) {
            sendError(response, ErrorCode.ACCOUNT_BANNED);
            return false;
        }

        // Token 黑名单检查（Phase 10: 管理员封号/强制下线后 token 加入黑名单）
        if (isTokenBlacklisted(userId)) {
            sendError(response, ErrorCode.TOKEN_EXPIRED, "Token 已失效，请重新登录");
            return false;
        }

        // 校验 token_version（Gateway 传递的值 vs DB 当前值）
        String gatewayTv = request.getHeader("X-Token-Version");
        if (gatewayTv != null && !gatewayTv.isEmpty()) {
            try {
                int tv = Integer.parseInt(gatewayTv);
                if (!Objects.equals(user.getTokenVersion(), tv)) {
                    sendError(response, ErrorCode.TOKEN_VERSION_MISMATCH);
                    return false;
                }
            } catch (NumberFormatException ignored) {
                // token_version 解析失败不阻塞，DB 已确认用户存在且未封禁
            }
        }

        injectPrincipal(user);
        log.debug("Gateway 信任路径: userId={}, role={}", userId, user.getRole());
        return true;
    }

    /**
     * 直接访问路径 — 完整 JWT 验证（本地开发 / 无 Gateway 场景）
     *
     * @return true 鉴权通过，false 已写出错误响应
     */
    private boolean authenticateFromJwt(HttpServletRequest request,
                                        HttpServletResponse response) throws IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, ErrorCode.UNAUTHORIZED);
            return false;
        }
        String token = authHeader.substring(7);

        JwtPayload payload;
        try {
            payload = jwtUtil.validateAccessToken(token);
        } catch (Exception e) {
            log.warn("JWT 验证失败: {}", e.getMessage());
            sendError(response, ErrorCode.TOKEN_EXPIRED);
            return false;
        }

        User user = userRepo.findById(payload.getSub());
        if (user == null) {
            sendError(response, ErrorCode.TOKEN_EXPIRED, "用户不存在");
            return false;
        }
        if (!Objects.equals(user.getTokenVersion(), payload.getTv())) {
            sendError(response, ErrorCode.TOKEN_VERSION_MISMATCH);
            return false;
        }
        if ("banned".equals(user.getStatus())) {
            sendError(response, ErrorCode.ACCOUNT_BANNED);
            return false;
        }

        // Token 黑名单检查（Phase 10）
        if (isTokenBlacklisted(payload.getSub())) {
            sendError(response, ErrorCode.TOKEN_EXPIRED, "Token 已失效，请重新登录");
            return false;
        }

        injectPrincipal(user);
        return true;
    }

    // ============================================================
    // Token 黑名单（Phase 10）
    // ============================================================

    /**
     * 检查用户是否在黑名单中
     *
     * <p>黑名单用于强制下线场景（管理员封号、账号异常等）。
     * 每个用户一条独立 String key（{@code shm:token:blacklist:<userId>}），
     * 7 天 TTL 自动过期，无需手动清理。
     */
    private boolean isTokenBlacklisted(Long userId) {
        try {
            String key = RedisKeys.tokenBlacklistKey(userId);
            Boolean exists = redis.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            // Redis 不可用时放行（可用性优先，token_version 仍有保障）
            log.warn("Token 黑名单检查失败（Redis 不可用），降级放行: userId={}", userId);
            return false;
        }
    }

    /** 将用户注入 SecurityContextHolder */
    private void injectPrincipal(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    // ===== 内部方法 =====

    /** 检查请求是否在白名单中 */
    private boolean isWhitelisted(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return WHITELIST.stream()
                .anyMatch(e -> e.method.equals(method) && e.path.equals(path));
    }

    /** 输出统一 JSON 错误响应（与 GlobalExceptionHandler 格式一致） */
    private void sendError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        sendError(response, errorCode, errorCode.getMessage());
    }

    private void sendError(HttpServletResponse response, ErrorCode errorCode, String message)
            throws IOException {
        response.setStatus(mapHttpStatus(errorCode.getCode()));
        response.setContentType("application/json;charset=UTF-8");
        String body = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                errorCode.getCode(), message);
        response.getWriter().write(body);
    }

    /** HTTP 状态码映射（与 GlobalExceptionHandler 一致） */
    private int mapHttpStatus(int code) {
        if (code >= 1000 && code < 2000) return 401;
        if (code >= 2000 && code < 3000) return 404;
        if (code >= 3000 && code < 4000) return 409;
        if (code >= 4000 && code < 5000) return 400;
        if (code >= 5000 && code < 6000) return 403;
        return 500;
    }

    /** 白名单条目 */
    private static class WhitelistEntry {
        final String method;
        final String path;

        WhitelistEntry(String method, String path) {
            this.method = method;
            this.path = path;
        }
    }
}
