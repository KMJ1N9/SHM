package com.shm.core.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 内部 API 鉴权拦截器 — 校验 {@code X-Internal-Token} 请求头
 *
 * <p>仅对 {@code /internal/**} 路径生效。当 {@code internal.token} 配置为空时
 * （本地开发环境）直接放行，不阻塞调试。
 *
 * <p>安全模型（defense-in-depth）：
 * <ol>
 *   <li>Gateway 安全边界：{@code /internal/**} 路径禁止外部访问</li>
 *   <li>本拦截器第二层防护：若 Gateway 被绕过或直连 core-service 端口，
 *       缺少有效 Token 的请求返回 401</li>
 * </ol>
 *
 * <p>与 im-connector 的 {@code InternalAuthInterceptor} 模式一致。
 */
public class InternalAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthInterceptor.class);

    private static final String HEADER_NAME = "X-Internal-Token";

    /** 配置的内部 Token（来自 {@code internal.token} 属性） */
    private final String expectedToken;

    /** 是否启用校验（Token 为空时禁用，方便本地开发） */
    private final boolean enabled;

    public InternalAuthInterceptor(String expectedToken) {
        this.expectedToken = expectedToken;
        this.enabled = expectedToken != null && !expectedToken.isBlank();
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if (!enabled) {
            return true; // 未配置 Token，跳过校验
        }

        String provided = request.getHeader(HEADER_NAME);
        if (expectedToken.equals(provided)) {
            return true;
        }

        log.warn("内部 API 认证失败: {} {} (期望 Token 长度={}, 实际 Token {}null)",
                request.getMethod(), request.getRequestURI(),
                expectedToken.length(),
                provided == null ? "=" : "长度=" + provided.length());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write(
                    "{\"code\":1001,\"message\":\"未授权的内部 API 访问\",\"data\":null}");
        } catch (Exception ignored) {
            // 响应写入失败，放弃处理
        }
        return false;
    }
}
