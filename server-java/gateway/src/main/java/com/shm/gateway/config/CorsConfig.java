package com.shm.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS 跨域配置 — 允许小程序 + Web 管理后台跨域访问
 *
 * <p>微信小程序通过 wx.request 发起请求不受浏览器 CORS 策略限制，
 * 此处 CORS 配置主要面向：
 * <ul>
 *   <li>Web 管理后台（未来可能开发）</li>
 *   <li>开发调试工具（HBuilder X / Postman / curl）</li>
 * </ul>
 *
 * <p>使用 allowedOriginPatterns（非 allowedOrigins）以同时支持
 * credentials 和通配符，避免 CORS 规范冲突。
 */
@Configuration
public class CorsConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        // 前端可能需要读取的自定义响应头
        config.addExposedHeader(HttpHeaders.AUTHORIZATION);
        config.addExposedHeader("X-Trace-Id");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
