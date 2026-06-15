package com.shm.admin.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当前用户参数注解 — Controller 方法参数标注后自动注入 UserPrincipal
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    /** 是否必须登录，默认 true（未登录时抛 UNAUTHORIZED） */
    boolean required() default true;
}
