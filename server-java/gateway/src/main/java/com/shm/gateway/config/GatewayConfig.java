package com.shm.gateway.config;

import com.shm.common.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 模块配置 — 创建 Gateway 所需的 Bean
 *
 * <p>注意：不使用 {@code scanBasePackages = "com.shm"}，避免加载 common 模块
 * 的 WebMVC 组件（JacksonConfig 的 ObjectMapper 可能冲突、GlobalExceptionHandler
 * 不适用于 WebFlux 环境）。Gateway 所需 Bean 全部在本配置类中显式创建。
 *
 * <p>创建的 Bean：
 * <ul>
 *   <li>{@link JwtUtil} — JWT 签名验证（仅 Access Token 校验，不需要数据库）</li>
 * </ul>
 */
@Configuration
public class GatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    /**
     * 创建 JwtUtil Bean（与 core-service 共享同一个 common 类，密钥从环境变量注入）
     *
     * <p>Gateway 仅需 Access Token 密钥（用于验证），Refresh Token 密钥此处传占位值。
     * 实际 Refresh Token 由 core-service 直接处理（/api/auth/refresh 在白名单中）。
     */
    @Bean
    public JwtUtil jwtUtil(
            @Value("${jwt.access-secret}") String accessSecret,
            @Value("${jwt.refresh-secret:}") String refreshSecret) {
        if (accessSecret == null || accessSecret.isBlank()) {
            log.warn("jwt.access-secret 未配置，Gateway 鉴权将无法验证 Token。"
                    + "请设置环境变量 JWT_ACCESS_SECRET。");
        }
        return new JwtUtil(accessSecret, refreshSecret != null ? refreshSecret : "");
    }
}
