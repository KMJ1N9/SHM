package com.shm.core.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当前用户参数注解 — Controller 方法参数标注后自动注入 UserPrincipal
 *
 * <pre>{@code
 * @GetMapping("/api/auth/me")
 * public Map<String, Object> me(@CurrentUser UserPrincipal user) {
 *     return ResponseBuilder.ok(authService.me(user.getUserId()));
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    /** 是否必须登录，默认 true（未登录时抛 UNAUTHORIZED），设为 false 则允许游客访问 */
    boolean required() default true;
}
