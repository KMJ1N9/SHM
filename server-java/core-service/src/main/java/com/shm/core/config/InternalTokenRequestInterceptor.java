package com.shm.core.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Feign 内部调用请求拦截器 — 向所有 Feign 请求添加 {@code X-Internal-Token} 请求头
 *
 * <p>im-connector 的 {@code InternalAuthInterceptor} 校验此头。
 * 当 {@code internal.token} 为空时（本地开发），不注入 Token 头。
 */
@Component
public class InternalTokenRequestInterceptor implements RequestInterceptor {

    private final String internalToken;

    public InternalTokenRequestInterceptor(@Value("${internal.token:}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (internalToken != null && !internalToken.isEmpty()) {
            template.header("X-Internal-Token", internalToken);
        }
    }
}
