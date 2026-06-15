package com.shm.im.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * IM Connector Web MVC 配置 — 注册内部 API 鉴权拦截器
 *
 * <h3>拦截路径</h3>
 * <p>{@code /internal/**} — 所有内部 API 端点均需 {@code X-Internal-Token} 校验。
 *
 * <h3>Token 来源</h3>
 * <p>通过环境变量 {@code INTERNAL_TOKEN} 注入，与调用方（core-service / admin-service）
 * 的 Feign 客户端使用同一密钥。
 *
 * <p>本地开发时 Token 为空 → 拦截器自动放行，无需额外配置。
 */
@Configuration
public class InternalAuthConfig implements WebMvcConfigurer {

    @Value("${internal.token:}")
    private String internalToken;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalAuthInterceptor(internalToken))
                .addPathPatterns("/internal/**");
    }
}
