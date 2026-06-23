package com.shm.admin.security;

import com.shm.admin.mapper.UserMapper;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.entity.User;
import com.shm.common.util.JwtPayload;
import com.shm.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/**
 * Admin Service JWT 鉴权过滤器
 *
 * <h3>双路径鉴权（Phase 7 Gateway 适配）</h3>
 * <ol>
 *   <li><b>Gateway 信任路径</b> — 请求头含 {@code X-User-Id} 时，跳过 JWT 加密验证，
 *       信任 Gateway 的签名校验结果，仅做 DB 防御检查（用户存在/封禁/tokenVersion）</li>
 *   <li><b>直接访问路径</b> — 无 {@code X-User-Id} 时，完整 JWT 签名验证 + DB 检查
 *       （本地开发无 Gateway 场景）</li>
 * </ol>
 *
 * <p>额外角色校验：仅 admin / cs 可访问管理后台接口。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /** 管理后台允许的角色 */
    private static final Set<String> ALLOWED_ROLES = Set.of("admin", "cs");

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final Set<String> adminAutoPhones;

    public JwtAuthFilter(JwtUtil jwtUtil, UserMapper userMapper, String adminAutoPhones) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.adminAutoPhones = (adminAutoPhones != null && !adminAutoPhones.isBlank())
                ? Set.of(adminAutoPhones.split("\\s*,\\s*"))
                : Set.of();
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

        // 1b. Swagger UI + API docs 放行（Phase 12）
        String uri = request.getRequestURI();
        if (uri.startsWith("/v3/api-docs") || uri.startsWith("/swagger-ui")
                || uri.startsWith("/webjars")) {
            chain.doFilter(request, response);
            return;
        }

        // 2. 健康检查放行
        if ("GET".equals(request.getMethod()) && "/api/health".equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // 3. Gateway 透传路径：X-User-Id 头存在 → 信任 Gateway 的 JWT 验证结果
        String gatewayUserId = request.getHeader("X-User-Id");
        if (gatewayUserId != null && !gatewayUserId.isEmpty()) {
            if (!authenticateFromGateway(request, response, gatewayUserId)) {
                return;
            }
        } else {
            // 4. 直接访问路径：完整 JWT 验证
            if (!authenticateFromJwt(request, response)) {
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Gateway 信任路径 — 跳过 JWT 加密验证，仍做 DB 防御检查 + 角色校验
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

        User user = userMapper.findById(userId);
        if (user == null) {
            sendError(response, ErrorCode.TOKEN_EXPIRED, "用户不存在");
            return false;
        }
        if ("banned".equals(user.getStatus())) {
            sendError(response, ErrorCode.ACCOUNT_BANNED);
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
                // token_version 解析失败不阻塞
            }
        }

        // 管理员自动提升：admin.auto-phones 白名单中的手机号自动获得 admin 角色
        // 与 AuthService.login() 中的逻辑互补 — login 在登录时提升，此处作为安全网
        // 覆盖未重新登录但角色仍未升级的用户
        if (adminAutoPhones != null && !adminAutoPhones.isEmpty()
                && user.getPhone() != null && adminAutoPhones.contains(user.getPhone())
                && !"admin".equals(user.getRole())) {
            userMapper.updateRole(user.getId(), "admin");
            user.setRole("admin");
            log.info("管理员自动提升(admin-service Gateway): userId={}", user.getId());
        }

        // 角色校验：仅 admin / cs 可访问
        if (user.getRole() == null || !ALLOWED_ROLES.contains(user.getRole())) {
            sendError(response, ErrorCode.ROLE_REQUIRED);
            return false;
        }

        injectPrincipal(user);
        log.debug("Admin Gateway 信任路径: userId={}, role={}", userId, user.getRole());
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
            log.warn("Admin JWT 验证失败: {}", e.getMessage());
            sendError(response, ErrorCode.TOKEN_EXPIRED);
            return false;
        }

        User user = userMapper.findById(payload.getSub());
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

        // 管理员自动提升：admin.auto-phones 白名单中的手机号自动获得 admin 角色
        if (adminAutoPhones != null && !adminAutoPhones.isEmpty()
                && user.getPhone() != null && adminAutoPhones.contains(user.getPhone())
                && !"admin".equals(user.getRole())) {
            userMapper.updateRole(user.getId(), "admin");
            user.setRole("admin");
            log.info("管理员自动提升(admin-service JWT): userId={}", user.getId());
        }

        // 角色校验：仅 admin / cs 可访问
        if (user.getRole() == null || !ALLOWED_ROLES.contains(user.getRole())) {
            sendError(response, ErrorCode.ROLE_REQUIRED);
            return false;
        }

        injectPrincipal(user);
        return true;
    }

    /** 将用户注入 SecurityContextHolder */
    private void injectPrincipal(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    // ===== 内部方法 =====

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

    private int mapHttpStatus(int code) {
        if (code >= 1000 && code < 2000) return 401;
        if (code >= 2000 && code < 3000) return 404;
        if (code >= 3000 && code < 4000) return 409;
        if (code >= 4000 && code < 5000) return 400;
        if (code >= 5000 && code < 6000) return 403;
        return 500;
    }
}
