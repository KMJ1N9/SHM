package com.shm.common.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson 全局配置 — 确保 JSON 输入/输出与前端和 Node.js 后端兼容
 *
 * <h3>核心设计：序列化与反序列化均使用蛇形命名</h3>
 * <p>
 * 使用 {@link Jackson2ObjectMapperBuilderCustomizer} 而非直接创建 {@link com.fasterxml.jackson.databind.ObjectMapper}
 * Bean，确保 Spring Boot 的自动配置（模块注册、注解处理等）完整保留，仅在此基础上追加自定义策略。
 *
 * <ul>
 *   <li><b>命名策略</b>：SNAKE_CASE（双向）— creditScore ↔ credit_score</li>
 *   <li><b>日期格式</b>：ISO8601 — "2026-06-13T10:00:00.000Z"</li>
 *   <li><b>时区</b>：UTC — 与 JS new Date().toISOString() 一致</li>
 * </ul>
 *
 * <p>前端 uni-app 发送 snake_case JSON（如 refresh_token / product_id / original_price），
 * 与原 Node.js 后端一致。本配置确保 Java 后端与前端完全兼容。</p>
 *
 * @author Claude Code
 * @since 2026-06-13
 */
@Configuration
public class JacksonConfig {

    private static final String ISO8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    /**
     * 自定义 Spring Boot 自动配置的 ObjectMapper。
     *
     * <p>不创建新的 ObjectMapper Bean — 仅通过 Customizer 追加配置，
     * 保留 Spring Boot 自动配置的模块、注解处理等功能。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // snake_case 双向：序列化与反序列化均转蛇形（与前端一致）
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

            // ISO8601 日期格式 + UTC 时区
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(ISO8601_PATTERN);
            javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
            builder.modules(javaTimeModule);
            builder.timeZone(TimeZone.getTimeZone("UTC"));
        };
    }
}
