package com.shm.admin.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.RequestOriginParser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Sentinel 授权规则 — 请求来源解析器（Phase 17）
 *
 * <p>从 Gateway 注入的 {@code X-User-Role} 请求头提取角色，
 * 返回 {@code "admin"} 或 {@code "user"}，供 Sentinel 授权规则匹配白名单。
 *
 * <p>需在 {@code application.yml} 中配置 {@code spring.cloud.sentinel.filter.enabled: true}
 * 以使 Web MVC 拦截器生效。
 *
 * <p>Gateway {@code JwtAuthGatewayFilter} 已将用户角色写入该请求头，
 * 此处无需重复查询数据库。
 */
@Component
public class SentinelOriginConfig implements RequestOriginParser {

    private static final String ROLE_HEADER = "X-User-Role";
    private static final String ADMIN_ROLE = "admin";
    private static final String DEFAULT_ORIGIN = "user";

    @Override
    public String parseOrigin(HttpServletRequest request) {
        String role = request.getHeader(ROLE_HEADER);
        if (ADMIN_ROLE.equalsIgnoreCase(role)) {
            return ADMIN_ROLE;
        }
        return DEFAULT_ORIGIN;
    }
}
